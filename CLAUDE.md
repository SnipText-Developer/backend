# SNIPTEXT 백엔드

한글 특화 템플릿 클립보드 매니저(웹 대시보드 + 크롬 확장)의 API 서버. 실제 출시해 사용자를 받는 서비스다.
현재 범위는 S1(대시보드). 결제·구독, 확장 동기화(S2), 탈퇴는 아직 구현하지 않는다.

## 명령어

```bash
docker compose up -d                                   # 로컬 MySQL
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew build                                        # 빌드 + 테스트 + 형식 검사
./gradlew test                                         # 전체 테스트 (도커 실행 필요)
./gradlew test --tests "com.sniptext.domain.template.*"  # 일부만
```

- 형식 검사 도구: (정해지면 명령어 추가)
- 작업을 끝냈다고 말하기 전에 `./gradlew build`가 통과하는지 확인한다.

## 기술 스택

- 자바 21, 스프링 부트 4.1, 그래들(그루비 문법)
- 스프링 부트 4는 JSON 처리에 잭슨 3(`tools.jackson` 패키지)을 쓴다. 예전 `com.fasterxml.jackson.databind` 예제를 그대로 가져오지 않는다.
- JPA + QueryDSL, MySQL 8.4, 플라이웨이 (로컬 도커, 테스트컨테이너, RDS 모두 같은 버전)
- 스프링 시큐리티 + 토큰 인증 (접근 토큰 30분 / 재발급 토큰 14일)
- 외부 호출은 `RestClient`로 통일 (구글, AI, 이후 토스페이먼츠)
- 배치: 스프링 스케줄러 + 셰드락
- 테스트: JUnit 5, AssertJ, 모키토, 테스트컨테이너(MySQL)
- 레디스는 쓰지 않는다. 재발급 토큰도 DB 테이블에 저장한다.
- 인프라: AWS, 테라폼, 도커 컴포즈, 깃허브 액션. 콘솔 수동 변경 금지

## 기준 문서

구현 전에 해당 부분을 먼저 읽는다. 문서와 코드가 다르면 임의로 고르지 말고 물어본다.

- `docs/s1-api-spec.md`: S1(대시보드) API 명세 v0.7. 경로, 요청·응답 필드, 결과 코드와 에러 문구가 전부 여기 있다. 공통 규칙(응답 형식, 결과 코드, 시간, 입력 검증, 캐시)도 이 문서 앞부분에 있다.
- 이후 스프린트 명세는 `docs/s2-api-spec.md`처럼 추가된다. 작업 중인 API가 어느 문서에 있는지 먼저 확인한다.
- `docs/erd.md`: ERD v0.6과 DDL. 컬럼 의미, 인덱스, 설계 메모
- 구현 후에는 스웨거가 기준이다. 명세와 달라지는 변경은 PR 설명에 적는다.

## 패키지 구조

```
com.sniptext
├── domain
│   ├── auth         # 로그인·가입·재발급·로그아웃. SocialAccount, RefreshToken
│   ├── user         # User, UserTermsAgreement. 내 정보·설정·사용량, 가입 시 회원 생성
│   ├── template     # 내 템플릿 + 휴지통 + 휴지통 정리 배치
│   ├── gallery      # 갤러리 템플릿, 내 템플릿으로 저장
│   ├── ai           # AI 톤 교정, 하루 사용량, 핵심 정보 검사
│   └── plan         # 현재 플랜 조회 (PlanQueryService, 테이블 없음)
└── global
    ├── security     # 보안 설정, 토큰 필터·발급 도구, 로그인 회원 주입, 개발용 임시 토큰
    ├── client       # 외부 호출 (구글 토큰 검증, 언어모델)
    ├── config       # JPA, 출처 허용, 스케줄, 시각 직렬화
    ├── entity       # 공통 시각 부모 엔티티
    ├── exception    # 전역 예외 처리, 공통 에러 코드
    ├── response     # 공통 응답, 결과 코드 인터페이스
    └── crypto       # 본문 암호화 변환기
```

도메인 내부:

```
domain/{도메인}/
├── controller/      # {도메인}SuccessCode (성공 결과 코드 열거형)
│   └── dto/         # {도메인}{행위}Request · {도메인}{행위}Response
├── service/         # {도메인}CommandService (쓰기) · {도메인}QueryService (조회)
│   └── dto/
│       ├── command/ # {도메인}{행위}Command  (쓰기 입력)
│       ├── query/   # {도메인}{행위}Query    (조회 입력, 파라미터 2개 이상일 때)
│       └── result/  # {도메인}{행위}Result   (서비스 반환)
├── repository/
│   └── dto/         # {도메인}{대상}Row (조회 전용, 성능 문제가 확인됐을 때만)
├── entity/
└── exception/       # {도메인}ErrorCode (에러 결과 코드 열거형)
```

- 변환: `request.toCommand()`, `Response.from(result)`, `Result.from(entity)`, 여러 개 조합은 `of()`
- 의존 방향: `controller → service → repository · entity`. 컨트롤러는 레포지토리·엔티티를 직접 쓰지 않는다.
- 컨트롤러의 요청 객체를 서비스에 그대로 넘기지 않는다. `command`/`query`로 바꿔 넘기고, 서비스는 `result`를 돌려준다.
- 엔티티를 응답으로 내보내지 않는다.
- **다른 도메인은 그 도메인의 서비스만 부른다.** 남의 레포지토리·엔티티를 직접 쓰지 않는다. (예: 갤러리 저장 → `TemplateCommandService`)
- `global`은 `domain`을 참조하지 않는다.
- 성공 코드는 컨트롤러가, 에러 코드는 서비스가 쓴다. 서비스는 성공 코드를 모른다.
- 버전 구분(`V2`)이 필요하면 컨트롤러의 요청·응답 객체에만 붙인다.
- 값을 나타내는 열거형(정렬 기준, 플랜, 카테고리 등)은 그 값을 저장하는 엔티티의 도메인 `entity` 패키지에 둔다. 저장하지 않는 값은 그 값을 쓰는 도메인의 `entity` 패키지에 둔다.
- `entity` 패키지의 열거형은 컨트롤러와 다른 도메인에서 직접 써도 된다. 엔티티 클래스는 안 된다.
- 열거형 상수 이름은 API 값이 된다. 명세를 먼저 바꾸지 않고 상수 이름을 바꾸지 않는다.
- 의존 규칙은 ArchUnit 테스트가 강제한다. 테스트가 깨지면 구조를 고치고, 테스트를 고치지 않는다.

**도메인 사이 연결 지점** (메서드 모양을 먼저 정하고 각자 개발)

| 연결 | 메서드 | 만드는 쪽 → 부르는 쪽 |
| --- | --- | --- |
| 현재 플랜 조회 | `PlanResult getCurrentPlan(Long userId)` | plan → template, user, ai |
| 갤러리에서 템플릿 생성 | `TemplateCreateResult createFromGallery(TemplateCreateFromGalleryCommand)` | template → gallery |
| 보유 템플릿 개수 | `long countActive(Long userId)` | template → user |
| AI 오늘 사용량 | `AiToneUsageResult getTodayUsage(Long userId)` | ai → user |
| 가입 시 회원 생성 | `Long signUp(UserSignUpCommand)` | user → auth |
| 플랜 지정값 조회 | `Optional<Plan> findPlanOverride(Long userId)` (`UserQueryService`) | user → plan |
| 회원 행 잠금 | `void lockUser(Long userId)` (`UserCommandService`, 호출자 트랜잭션 필수) | user → template |

- **다른 도메인 서비스를 조합하는 조회는 별도 서비스로 분리한다.** 예: 사용량 API는 `UserUsageQueryService`
  - 순환 참조를 막기 위해서다. plan → `UserQueryService`인데 사용량 때문에 `UserQueryService` → plan을 넣으면 생성자 주입 순환으로 앱이 뜨지 않는다

## 코드 규칙

- 이름: `TemplateCreateRequest`, `TemplateResponse` / 변환 메서드는 `from`, `of`
- 요청·응답·명령·결과 객체는 `record`로 작성한다.
- 엔티티: 세터 금지, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 연관관계는 지연 로딩, 양방향은 꼭 필요할 때만. 값 변경은 의미 있는 메서드로 (`template.rename(...)`)
- 엔티티 생성: `create`로 시작하는 정적 팩토리로만 만든다. 생성 경로가 여럿이면 이름으로 구분한다 (예: `Template.create`, `Template.createFromGallery`). 빌더는 `@Builder(access = AccessLevel.PRIVATE)`로 팩토리 안에서만 쓴다
  - 팩토리는 불변 조건(필수 값, 길이 등)만 검사하고 위반은 `IllegalArgumentException`. 바깥 입력은 요청 객체와 서비스에서 이미 검증하므로 여기서 걸리면 코드 실수라 500이 맞다
  - 화면에 보일 에러 코드는 지금처럼 서비스가 던진다
  - 엔티티 패키지에 생성 전용 객체를 두지 않는다. 계층별 이름(`{도메인}{행위}Command` 등)과 겹친다. 인자가 많으면 팩토리 안에서 빌더로 처리한다
  - 테스트에서는 픽스처로 만든다 (테스트 참고)
  - 기존 `User`는 아직 공개 빌더다. 가입 이슈에서 `create`로 바꾸고 테스트도 픽스처로 옮긴다. 그 전까지 `User.builder()`를 새 코드의 예시로 삼지 않는다
- 롬복 허용: `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder(access = PRIVATE)`(정적 팩토리 안에서만), `@RequiredArgsConstructor`, `@Slf4j`
- 롬복 금지: `@Setter`, `@Data`, `@AllArgsConstructor`, 엔티티의 `@ToString`·`@EqualsAndHashCode`
- 트랜잭션: 서비스 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`
- 검증: 형식 검증은 요청 객체(빈 검증), 업무 규칙 검증은 서비스

## 응답·예외

모든 응답은 공통 형식으로 감싼다.

```json
{ "isSuccess": true, "code": "TEMPLATE200_1", "message": "...", "result": { } }
```

- 결과 코드: `{도메인}{HTTP 상태}_{번호}` (예: `TEMPLATE409_1`). 도메인: COMMON, AUTH, USER, TEMPLATE, TRASH, GALLERY, AI
- 코드와 문구는 `docs/s1-api-spec.md`(이후 스프린트는 해당 명세)에 있는 그대로 쓴다. 문구는 화면에 그대로 뜨므로 바꾸지 않는다.
- 에러일 때와 돌려줄 데이터가 없을 때 `result`는 `null`
- 검증 에러가 여러 개여도 첫 번째 하나만 내려준다. 필드별 코드가 없는 형식 오류는 `COMMON400_1`
- 도메인 에러는 도메인별 열거형으로 정의하고 예외로 던진다. 컨트롤러에서 직접 에러 응답을 만들지 않는다.
- **남의 리소스 요청은 403이 아니라 404**로 응답한다 (존재 여부 노출 방지). 조회·수정·삭제는 항상 본인 소유 조건으로 찾는다.

## 시간

- DB는 한국 시간 `DATETIME(6)`으로 저장한다. 엔티티는 `LocalDateTime`
- JVM 기본 시간대와 DB 접속 시간대를 `Asia/Seoul`로 고정한다.
- 응답의 모든 시각은 `yyyy-MM-dd'T'HH:mm:ssXXX` 형식으로 `+09:00`을 붙인다. 날짜만 있는 값(`yyyy-MM-dd`)에는 붙이지 않는다.
- 요청에 시간대 표시가 없는 시각이 오면 `COMMON400_1`
- 현재 시각은 `Clock` 빈에서 얻는다. 테스트에서 시각을 고정하기 위해서다.
- 운영 `Clock` 빈은 마이크로초 단위로 잘려 있다. 테스트용 시계도 같은 정밀도로 쓴다.

## DB·마이그레이션

- `ddl-auto`는 모든 환경에서 `validate` 또는 `none`. 스키마는 플라이웨이로만 바꾼다.
- 파일 이름: `V{yyyyMMddHHmm}__{설명}.sql` (예: `V202610231430__create_templates.sql`). 번호 충돌을 막기 위해 날짜시각을 쓴다.
- **이미 병합된 마이그레이션 파일은 절대 고치지 않는다.** 새 파일을 추가한다.
- 테이블·컬럼은 스네이크 표기. 인덱스 이름은 `uk_`, `idx_`, `fk_` 접두어 (ERD 기준)

## 도메인 규칙 (틀리기 쉬운 것)

**단축어**
- 형식 검사 **전에** 유니코드 NFC 정규화 (맥 자모 분리형 대응)
- 형식: `^/[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]{2,10}$`, 예약어 `/톤교정`
- `shortcut`은 입력 그대로 저장, `shortcut_key`는 정규화 + 소문자로 저장해 비교에 쓴다.
- `active_shortcut_key`는 DB 생성 컬럼이다. 엔티티에서 `insertable = false, updatable = false`로 매핑하고 직접 값을 넣지 않는다.
- 중복 판정은 휴지통을 제외한다. 저장 시 유니크 위반은 `TEMPLATE409_1`로 바꿔 응답한다.
- 갤러리 저장·휴지통 복구에서 충돌하면 원본 뒤에 2~99를 붙인다. `/` 뒤가 10자를 넘으면 원본을 뒤에서 잘라 맞춘다. 숫자로 끝나는 원본도 뒤에 붙인다(`/견적2` → `/견적22`). 모두 차면 `TEMPLATE409_2`

**본문·변수**
- 저장 전 `CRLF` → `LF`, 그다음 글자 수 검사. 글자 수는 `String.length()` (화면과 같은 기준)
- 변수: `\{\{([가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9_]+)\}\}`만 변수. 형식이 틀린 중괄호는 일반 글자로 두고 저장을 막지 않는다.
- 변수 목록은 저장하지 않는다. 조회 시 복호화한 본문에서 뽑아 첫 등장 순, 중복 제거로 내려준다.

**시각 컬럼의 의미** (섞지 말 것)
- `last_used_at`: 사용 기록 API에서만 갱신 → 최근 사용순
- `edited_at`: 제목·단축어·본문 수정 시에만 갱신 → 최근 수정순. 사용 기록·삭제·복구로는 바뀌지 않는다.
- `updated_at`: 모든 변경 → 확장 동기화 기준
- **대량 갱신 쿼리(`@Modifying`)는 자동 시각 기록을 거치지 않으므로 `updated_at`을 직접 넣는다**
  - 해당하는 곳: 사용 기록의 `last_used_at` 갱신(C), AI 사용량 조건부 증가(A), 휴지통 일괄 처리가 있다면 그것도
  - `updated_at`은 확장 동기화 기준이라 빠지면 S2에서 동기화가 조용히 틀어진다
- 사용 횟수는 저장하지 않는다.

**한도와 플랜**
- 템플릿 한도는 휴지통 제외 개수 기준 (FREE 5 / PRO 50)
- 생성·복구·갤러리 저장은 같은 트랜잭션에서 회원 행을 비관적 잠금으로 잡은 뒤 개수 검사 → 저장
- 한도 검사는 `lockUser`를 먼저 부른 같은 트랜잭션 안에서 한다
- 현재 플랜 판정과 한도 숫자(템플릿 5/50, AI 하루 10)는 `plan` 도메인의 `PlanQueryService` 한 곳에서만 한다. 지금은 `users.plan_override` 값, 없으면 FREE. 다른 곳에서 플랜이나 한도 숫자를 직접 쓰지 않는다.

**AI 톤 교정**
- PRO 전용, 하루 10회 (KST 날짜별 1행)
- 요청 시 `used_count < 10` 조건부 증가로 1회를 먼저 잡고, 외부 AI 오류·20초 타임아웃·혐오 표현 차단이면 되돌린다.
- 출력 토큰 상한 4000을 반드시 설정한다.
- **원문과 결과는 저장하지도, 로그에 남기지도 않는다.** 예외 로그에도 넣지 않는다.

**인증**
- 웹 로그인은 프론트가 보낸 구글 ID 토큰을 서버가 검증한다 (서명, 발급자, 대상, 만료, 이메일 인증 여부).
- 처음 온 사용자는 약관 동의 전까지 회원을 만들지 않는다. 가입용 토큰(30분)을 주고, 가입 API에서 회원·소셜 계정·약관 동의 2건을 한 트랜잭션으로 저장한다.
- 재발급 토큰은 원문 대신 SHA-256 해시를 저장한다. 쓸 때마다 교체하고, 폐기된 토큰이 다시 오면 같은 `family_id` 전체를 폐기한다.
- 웹의 재발급 토큰은 HttpOnly·Secure 쿠키로만 주고받는다.
- 로그아웃은 접근 토큰 없이 쿠키만으로 처리하고, 쿠키가 없거나 이미 폐기돼도 성공으로 응답한다.
- 개발용 임시 토큰 발급 경로는 `local`·`dev` 프로필에서만 켠다.
- 컨트롤러에서 로그인 회원은 `@LoginUser Long userId`로 받는다. 인증 정보(`SecurityContextHolder`)에서 직접 꺼내지 않는다.
- 접근 토큰이 없거나 만료되면 `COMMON401_1`, 위조·형식 오류·종류가 다른 토큰이면 `COMMON401_2`다. 필터는 사유만 요청 속성에 남기고, 응답은 진입점이 한다.
- 통합 테스트에서 인증된 요청은 `IntegrationTestSupport.bearer(userId)`로 만든다 (실제 발급 도구 사용). `@WithMockUser`로는 `@LoginUser`가 채워지지 않는다.
- 서명 키는 `JWT_SECRET` 환경 변수로 받고 32바이트 미만이면 기동에 실패한다. 개발 서버와 운영의 키는 반드시 다르게 둔다.
- 테스트용 서명 키는 `build.gradle` 테스트 작업의 `environment`로 넣는다. `src/test/resources/application.yml`을 만들지 않는다 (운영 `application.yml`을 통째로 가린다).

**캐시 헤더**
- `GET /api/v1/gallery/**`만 `Cache-Control: public, max-age=300`. 나머지는 전부 `no-store`
- 갤러리 경로라도 에러 응답(404, 500 등)은 `no-store` (에러가 5분간 캐시되지 않게)
- 공개 응답에는 사람마다 다른 값을 절대 넣지 않는다 (CDN이 다른 사람에게 돌려줄 수 있음).

**배치** (셰드락 필수)
- 매일 00:10 휴지통 30일 지난 템플릿 영구 삭제
- 매일 04:00 만료된 재발급 토큰 정리

## 보안·개인정보

- `templates.content`는 JPA 변환기로 AES-GCM 암호화한다. DB에서 본문을 검색하지 않는다 (검색은 화면에서).
- 갤러리 본문은 공개 데이터라 암호화하지 않는다.
- 템플릿 본문, 토큰, 이메일을 로그에 남기지 않는다.
- 비밀 값은 환경 변수로만 읽는다. `.env`와 실제 키를 커밋하지 않는다.

## 테스트

- 기능 하나에 테스트가 같이 들어간다. 테스트 없는 PR은 병합하지 않는다.
- 테스트 이름은 `@DisplayName`에 한국어로 쓴다.
- 서비스 단위 테스트는 모키토, 레포지토리·통합 테스트는 테스트컨테이너 MySQL (H2 쓰지 않음. 생성 컬럼·잠금 동작이 다르다)
- 테스트에서 엔티티는 `src/test/.../fixture/{엔티티}Fixture`로 만든다. 생성 인자가 바뀌어도 테스트가 한꺼번에 깨지지 않게 하기 위해서다
- 반드시 검증할 것: 본인 소유가 아니면 404, 한도 경계(4→5, 5→6), 단축어 정규화·대소문자 중복, 동시 요청 시 한도 초과 방지

## 깃·PR

- 브랜치: `main`(운영) / `develop`(개발 서버) / `feat/이슈번호-이름`, `fix/...`, `refactor/...`, `chore/...` (예: `feat/12-google-login`). 브랜치 이름에는 `#`을 쓰지 않는다.
- 커밋: `feat|fix|refactor|test|docs|chore: 한국어 설명 (#이슈번호)` 예) `feat: 구글 로그인 ID 토큰 검증 구현 (#12)`
  - `docs`: 문서만 바꿀 때 (CLAUDE.md, 명세, ERD). 코드와 같이 바뀌면 코드 쪽 접두어를 쓴다
- PR은 `develop` 대상, 변경 400줄 안팎. 넘으면 나눈다.

## 작업할 때 지킬 것

- 답변, 주석, 커밋 메시지는 한국어로 쓴다.
- `global/` 아래 공통 코드(응답 형식, 전역 예외 처리, 보안 설정)는 요청받지 않으면 수정하지 않는다. 필요하면 먼저 말한다.
- 명세에 없는 API·필드·에러 코드를 임의로 만들지 않는다.
- `main`·`develop`에 직접 커밋·푸시하지 않는다. 푸시와 PR 생성은 요청받았을 때만 한다.
- 실패하는 테스트를 지우거나 건너뛰게 만들어 통과시키지 않는다.
- 새 의존성을 추가할 때는 이유를 먼저 말한다.
