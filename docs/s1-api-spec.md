# SNIPTEXT API 명세 (S1 대시보드) v0.7

| 항목 | 내용 |
| --- | --- |
| 버전 | v0.7 (2026-10-17, 팀 결정 반영) |
| v0.6 대비 변경 | 모든 시각에 `+09:00` 시간대 표시 추가(공통 규칙 4장), 패키지 컨벤션 확정(10장). API 개수·필드 변경 없음 |
| 기본 경로 | `/api/v1` |
| API 수 | 22개 |

## 목록

| # | 도메인 | 메서드 | URI | 설명 | 로그인 | 캐시 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 인증 | POST | `/api/v1/auth/login` | 구글 로그인 (가입된 회원은 바로 로그인, 처음이면 약관 동의 단계로) | 불필요 | 금지 |
| 2 | 인증 | POST | `/api/v1/auth/signup` | 약관 동의 후 가입 완료 (첫 로그인 직후 동의 화면) | 불필요 | 금지 |
| 3 | 인증 | POST | `/api/v1/auth/reissue` | 접근 토큰 재발급 (재발급 토큰 교체) | 불필요 | 금지 |
| 4 | 인증 | POST | `/api/v1/auth/logout` | 로그아웃 (웹·확장 세션 모두 무효화) | 불필요 | 금지 |
| 5 | 회원 | GET | `/api/v1/users/me` | 내 정보 조회 (사이드바 프로필, 계정 팝오버) | 필요 | 금지 |
| 6 | 회원 | GET | `/api/v1/users/me/usage` | 플랜 사용량 조회 (사이드바 개수 뱃지, 플랜 카드, AI 남은 횟수) | 필요 | 금지 |
| 7 | 회원 | PATCH | `/api/v1/users/me/settings` | 내 템플릿 정렬 기준 저장 (다음 접속 시 유지) | 필요 | 금지 |
| 8 | 내 템플릿 | GET | `/api/v1/templates` | 내 템플릿 목록 조회 (휴지통 제외 전체) | 필요 | 금지 |
| 9 | 내 템플릿 | GET | `/api/v1/templates/{templateId}` | 내 템플릿 상세 조회 (미리보기 패널, 편집 모드 진입) | 필요 | 금지 |
| 10 | 내 템플릿 | POST | `/api/v1/templates` | 새 템플릿 저장 | 필요 | 금지 |
| 11 | 내 템플릿 | PUT | `/api/v1/templates/{templateId}` | 템플릿 수정 (편집 모드 저장) | 필요 | 금지 |
| 12 | 내 템플릿 | DELETE | `/api/v1/templates/{templateId}` | 템플릿 삭제 → 휴지통 이동 (소프트 삭제) | 필요 | 금지 |
| 13 | 내 템플릿 | GET | `/api/v1/templates/shortcuts/availability` | 단축어 중복·형식 실시간 확인 (입력 정지 0.5초 후) | 필요 | 금지 |
| 14 | 내 템플릿 | POST | `/api/v1/templates/{templateId}/usages` | 템플릿 사용 기록 (복사·삽입 성공 시 최근 사용 시각 갱신) | 필요 | 금지 |
| 15 | 휴지통 | GET | `/api/v1/trash/templates` | 삭제된 템플릿 목록 조회 (최근 삭제순) | 필요 | 금지 |
| 16 | 휴지통 | POST | `/api/v1/trash/templates/{templateId}/restore` | 휴지통 템플릿 복구 (내 템플릿으로 복원, 개수 +1) | 필요 | 금지 |
| 17 | 휴지통 | DELETE | `/api/v1/trash/templates/{templateId}` | 휴지통 템플릿 개별 영구 삭제 | 필요 | 금지 |
| 18 | 휴지통 | DELETE | `/api/v1/trash/templates` | 휴지통 비우기 (전체 영구 삭제) | 필요 | 금지 |
| 19 | 갤러리 | GET | `/api/v1/gallery/templates` | 갤러리 템플릿 전체 조회 | 불필요 | 공개 5분 |
| 20 | 갤러리 | GET | `/api/v1/gallery/templates/{galleryTemplateId}` | 갤러리 템플릿 상세 조회 (미리보기 패널) | 불필요 | 공개 5분 |
| 21 | 갤러리 | POST | `/api/v1/gallery/templates/{galleryTemplateId}/save` | 갤러리 템플릿을 내 템플릿으로 복사 저장 | 필요 | 금지 |
| 22 | AI 톤 교정 | POST | `/api/v1/ai/tone-corrections` | AI 톤 교정 실행 (Pro 전용, 하루 10회) | 필요 | 금지 |

**이번 범위에 없는 것**: 결제·구독(기획 정책 확정 후), 확장 동기화·확장용 토큰 발급(S2), 탈퇴(화면 명세 없음)

## 공통 규칙

### 1. 인증

```
Authorization: Bearer accessToken
Content-Type: application/json
```

| 구분 | 웹 대시보드 | 확장 (S2) |
| --- | --- | --- |
| 로그인 | 프론트가 구글 ID 토큰을 받아 `POST /api/v1/auth/login` → 처음이면 약관 동의 후 `POST /api/v1/auth/signup` | 확장 전용 토큰 발급 방식은 S2에서 확정 (웹의 재발급 토큰은 HttpOnly라 확장에 넘길 수 없음) |
| 접근 토큰 | 화면 메모리 보관, 30분 | 확장 로컬 저장소, 30분 |
| 재발급 토큰 | HttpOnly 쿠키, 14일 | 확장 로컬 저장소, 14일 |
| 재발급 | 쓸 때마다 새 토큰으로 교체, 이미 쓴 토큰이 다시 오면 계보 전체 폐기 | 동일 |

- "로그인: 불필요"로 표시된 공개 API에는 **토큰을 보내지 마세요.** 토큰이 붙은 요청은 CDN이 캐시하지 않습니다(7장).

### 2. 응답 형식

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_1",
  "message": "템플릿 목록 조회에 성공했습니다.",
  "result": { }
}
```

- 성공인데 돌려줄 데이터가 없으면 `result`는 `null`입니다 (예: 로그아웃).
- 에러일 때 `result`는 `null`로 통일합니다.
- 에러 `message`는 화면에 그대로 띄워도 되는 문구로 씁니다 (기능명세 카피와 동일).

**에러는 두 종류입니다. 응답 모양은 같고 `code`로 구분합니다.**

| 종류 | 언제 | code | 화면 처리 |
| --- | --- | --- | --- |
| 도메인 에러 | 규칙 위반 (한도 초과, 단축어 중복, 없는 템플릿 등) | 각 API의 에러 코드 (예: `TEMPLATE409_1`) | 코드별로 모달·토스트·필드 에러 |
| 검증 에러 | 입력값 형식 위반 (빈 이름, 단축어 형식, 글자 수 초과 등) | 필드별 에러 코드 (예: `TEMPLATE400_3`). 여러 필드가 틀려도 **첫 번째 하나만** | 코드로 어느 필드인지 알 수 있으니 해당 입력창에 에러 + 포커스 |

- 필드별 코드가 따로 정해지지 않은 형식 오류(JSON 깨짐, 타입 불일치, 필수값 누락)는 `COMMON400_1`입니다.
- 검증 에러도 `result`는 `null`입니다. 어느 필드인지는 `code`가 알려주므로 필드 목록을 따로 싣지 않습니다.

### 3. 결과 코드

`{도메인}{HTTP 상태}_{번호}` 형식. 에러 표의 HttpStatus 칸은 HTTP 상태 숫자, ResultCode 칸은 응답의 `code` 값입니다.

| 도메인 | 범위 |
| --- | --- |
| COMMON | 공통 |
| AUTH | 로그인·가입·재발급·로그아웃 |
| USER | 내 정보·사용량·설정 |
| TEMPLATE | 내 템플릿 |
| TRASH | 휴지통 |
| GALLERY | 템플릿 갤러리 |
| AI | AI 톤 교정 |
| SUBSCRIPTION | 요금제·구독·결제 (결제 도입 시 사용) |

**공통 에러**

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 400 | COMMON400_1 | 잘못된 요청입니다. | 형식 오류 (JSON 파싱 실패, 타입 불일치 등) |
| 401 | COMMON401_1 | 로그인이 필요해요. | 접근 토큰 없음 또는 만료 → 재발급 시도, 실패하면 로그인 화면 |
| 401 | COMMON401_2 | 다시 로그인해주세요. | 위조·폐기된 접근 토큰 |
| 403 | COMMON403_1 | 접근 권한이 없어요. | 권한 없는 리소스 |
| 404 | COMMON404_1 | 요청한 주소를 찾을 수 없어요. | 없는 API 경로 |
| 405 | COMMON405_1 | 지원하지 않는 요청 방식이에요. | 메서드 불일치 |
| 500 | COMMON500_1 | 서버에 문제가 생겼어요. 잠시 후 다시 시도해주세요. | 서버 내부 오류 |

> 다른 사람의 템플릿 ID로 요청하면 403이 아니라 404를 돌려줍니다 (존재 여부 노출 방지).

### 4. 날짜·시간

| 종류 | 형식 | 예시 |
| --- | --- | --- |
| 시각 | `yyyy-MM-dd'T'HH:mm:ssXXX` (한국 시간, 시간대 표시 포함) | `2026-10-07T10:15:00+09:00` |
| 날짜 | `yyyy-MM-dd` | `2026-10-14` |

- 모든 시각 응답에는 `+09:00`을 붙입니다. 화면은 문자열 그대로 `new Date()`에 넣으면 기기 시간대와 상관없이 같은 순간으로 해석됩니다.
- 요청에 시각을 보낼 일이 생기면 같은 형식으로 보냅니다. 시간대 표시가 없는 시각은 받지 않습니다(COMMON400_1).
- 서버 저장은 그대로 한국 시간 `DATETIME(6)`이고, 응답으로 내보낼 때 `+09:00`을 붙입니다.
- 날짜만 있는 값(`yyyy-MM-dd`)에는 붙이지 않습니다.
- "방금 전" 같은 상대 표기는 화면에서 계산합니다.

### 5. 입력 검증 (화면·확장·서버 공통)

| 항목 | 규칙 |
| --- | --- |
| 단축어 형식 | 아래 "단축어 정규식" 참고. "/" 뒤 한글(완성형·자모)·영문·숫자 2~10자 |
| 단축어 정규화 | 형식 검사 **전에** 유니코드 NFC 정규화 (맥 자모 분리형 대응) |
| 단축어 비교 | 정규화 후 소문자로 비교. 저장·표시는 NFC 정규화한 값이고 대소문자는 입력 그대로 (자모 분리형을 그대로 두면 `VARCHAR(11)`을 넘음) |
| 예약어 | `/톤교정` |
| 변수 | 아래 "변수 정규식"에 맞는 것만 변수, 나머지 중괄호 표현은 일반 글자 |
| 변수 | 타입은 텍스트만 (MVP 확정). 모든 변수 필수 |
| 변수 목록 | 첫 등장 순, 같은 이름은 1개 |
| 템플릿 이름 | NFC 정규화 → 가운데 줄바꿈·탭을 공백 하나로 → 앞뒤 공백 제거 후 1~50자 |
| 템플릿 본문 | NFC 정규화 → 줄바꿈 문자를 한 가지로 통일한 뒤 1~3000자. 앞뒤 공백과 줄바꿈은 그대로 저장 |
| AI 원문 | 1~3000자 |
| 공백 | "공백만 입력"·"앞뒤 공백 제거"의 공백은 JS `trim`과 같은 범위 (일반 공백·탭·줄바꿈과 NBSP `U+00A0`·BOM `U+FEFF`·전각 공백 등 유니코드 공백) |
| 글자 수 | 화면(JS `length`)과 서버(Java `length()`)가 같도록 UTF-16 단위. 이름·본문은 **화면도 NFC로 정규화한 뒤** 센다 (맥 자모 분리형은 "견적"이 6자로 세어짐) |

**단축어 정규식**

```
^/[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]{2,10}$
```

**변수 정규식**

```
\{\{([가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9_]+)\}\}
```

**줄바꿈 통일**: 저장 전에 `CRLF`(윈도우 줄바꿈)를 `LF`로 바꿉니다.

### 6. 열거형

| 이름 | 값 |
| --- | --- |
| 정렬 기준 | `RECENT_USED` 최근 사용순(기본) / `RECENT_EDITED` 최근 수정순 |
| 사용 채널 | `DASHBOARD_COPY` / `POPUP_COPY` / `EMAIL_INLINE` |
| 갤러리 카테고리 | `WORK` 업무 / `SALES` 영업 / `CUSTOMER_SERVICE` 고객 응대 / `PERSONAL` 개인 (전체 탭은 화면에서 처리) |
| 문체 | `BOSS_EXTERNAL` 상사/외부용 / `REPORT_OFFICIAL` 보고/공문용 / `MARKETING` 마케팅/설득 / `NOTICE` 공지/안내 / `SOFT_POLITE` 부드러운 존댓말 / `CASUAL` 편한 반말 / `ESSAY` 감성/에세이 |
| 플랜 | `FREE` / `PRO` |
| 로그인 제공자 | `GOOGLE` |

### 7. 캐시 규칙

| 구분 | 대상 | 응답 헤더 |
| --- | --- | --- |
| 공개 데이터 | 갤러리 목록·상세 | `Cache-Control: public, max-age=300` |
| 개인 데이터 | 그 외 전부 (로그인 포함) | `Cache-Control: no-store` |

- CDN 규칙은 경로 앞부분으로 겁니다: **`GET /api/v1/gallery/**`만 공개 캐시** (결제 도입 시 요금제 조회 추가), 나머지는 전부 캐시 금지. 개인 데이터는 `/users/me/**`, `/templates/**` 등 공개 경로 밖에 둡니다.
- 캐시는 GET만 대상입니다. `POST /gallery/templates/{id}/save`처럼 공개 경로 아래에 있어도 GET이 아니면 캐시되지 않습니다.
- 공개 API 응답에는 **사람마다 다른 값을 절대 넣지 않습니다.** 넣는 순간 CDN이 한 사람의 응답을 다른 사람에게 돌려줄 수 있습니다. 그래서 갤러리 응답에는 저장 여부 같은 개인 값이 없습니다.
- 갤러리를 고치면 최대 5분 동안 이전 내용이 보일 수 있습니다 (허용 범위).
- 서버 캐시(Redis 등)와 실제 CDN 구축은 이번 범위가 아닙니다. 헤더만 지금 맞춰 두면 나중에 CDN은 설정만 하면 됩니다.

### 8. 목록 페이지 나눔

| 대상 | 방식 | 이유 |
| --- | --- | --- |
| 내 템플릿 | 나누지 않음 (전체) | 최대 50개, 통합 검색은 화면에서 (11장) |
| 휴지통 | 커서 방식 (`cursor`, `size`) | 30일간 개수 제한 없이 쌓임 |
| 갤러리 | 나누지 않음 (전체) | 100개 이하 (기획 확정). 카테고리·통합 검색은 화면에서 (11장) |

휴지통 응답: `hasNext`(다음 존재 여부), `nextCursor`(다음 요청에 그대로 넣는 값, 마지막이면 `null`)

### 9. 현재 플랜 (결제 도입 전)

- 템플릿 한도(Free 5 / Pro 50)와 AI 톤 교정 Pro 전용 검사는 **"현재 플랜 조회" 한 곳**만 부릅니다.
- 결제 도입 전에는 회원의 `plan_override` 값, 없으면 FREE로 판정합니다. 테스트 계정은 DB에서 `plan_override = 'PRO'`로 바꿔 씁니다.
- 결제·구독 정책이 확정되면 이 판정 안쪽만 구독 테이블 기준으로 바꾸고, API 응답 형태는 그대로 둡니다.

### 10. 팀 결정 사항

| 항목 | 결정 | 비고 |
| --- | --- | --- |
| 응답 형식 | 이 문서 형식 (`isSuccess / code / message / result`) | 확정 |
| 패키지 구조 | `com.sniptext.domain.{도메인}/controller(+dto) · service(+dto/command·query·result, {도메인}CommandService 쓰기 · {도메인}QueryService 조회) · repository(+dto) · entity · exception`, `com.sniptext.global/...` | 확정. 팀 깃허브·패키징 컨벤션 문서 참고, 의존 규칙은 ArchUnit 테스트로 강제 |
| DB | MySQL 8.4 | 확정 |
| 외부에 보이는 ID | 숫자 자동 증가 ID(Long) 그대로 사용 | 모든 조회·수정은 본인 소유 확인 후 처리, 남의 것이면 404 |
| 시간 표기 | 모든 시각에 `+09:00` 붙임 | 확정. 4장 참고 |
| 서비스 주소 구조 | 예) 웹 `sniptext.io`, API `api.sniptext.io` | 배포 전 결정. 재발급 쿠키 설정, 외부 출처 요청 허용 범위가 이걸로 정해짐 |

### 11. 상단 통합 검색 (DSH-09)

**검색 전용 API는 만들지 않습니다.** 화면이 아래 두 목록을 합쳐서 검색합니다.

| 대상 | 가져오는 API | 개수 |
| --- | --- | --- |
| 내 템플릿 | `GET /api/v1/templates` | 최대 50개 |
| 템플릿 갤러리 | `GET /api/v1/gallery/templates` | 100개 이하 |

- 서버에서 검색하지 않는 이유: 합쳐도 150개 이하라 화면 검색이 더 빠르고, 내 템플릿 본문은 암호화되어 있어 DB에서 본문 검색을 할 수 없습니다.
- 검색 범위는 로그인한 화면 전체 공통입니다. 검색을 시작했는데 갤러리 목록을 아직 받지 않았다면 그때 한 번 받습니다.

**검색 규칙 (화면에서 이대로 구현)**

| 항목 | 규칙 |
| --- | --- |
| 검색어 정리 | 앞뒤 공백 제거. 비어 있으면 검색하지 않고 이전 화면 유지 |
| 정규화 | 검색어와 대상 모두 유니코드 NFC 정규화 후 소문자로 비교 (맥 자모 분리형 대응) |
| 일치 조건 | 제목·본문·단축어 중 하나라도 검색어를 포함하면 일치 |
| 본문 | 저장된 원문 그대로 검색 (`{{고객명}}` 같은 변수 표기 포함) |
| 결과 구분 | 내 템플릿 결과와 갤러리 결과를 나눠서 표시 (6-1 화면) |

- 확장 프로그램(S2)에서 서버 검색이 필요해지면 그때 따로 추가합니다.


## 인증

### 1. POST /api/v1/auth/login

| 항목 | 내용 |
| --- | --- |
| 설명 | 구글 로그인 (가입된 회원은 바로 로그인, 처음이면 약관 동의 단계로) |
| 관련 기능 | 랜딩 로그인 (S1은 대시보드 임시 로그인 버튼), PRD 8.2 |
| 로그인 | 불필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Content-Type: application/json
```

##### Parameter

없음

##### Body

| 필드 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| idToken | String | Y | 프론트가 구글 로그인으로 받은 ID 토큰 |
| provider | String | Y | GOOGLE (S1은 구글만) |

##### Example

```
POST /api/v1/auth/login

{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
  "provider": "GOOGLE"
}
```

#### Response

##### 성공 예시 (가입된 회원 → 바로 로그인, 재발급 토큰은 쿠키로)

```json
{
  "isSuccess": true,
  "code": "AUTH200_2",
  "message": "로그인에 성공했습니다.",
  "result": {
    "isCompletedSignUp": true,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1,
    "nickname": "홍길동",
    "signupToken": null,
    "email": null,
    "name": null,
    "profileImageUrl": null
  }
}
```

##### 성공 예시 (처음 온 사용자 → 약관 동의 화면으로)

```json
{
  "isSuccess": true,
  "code": "AUTH200_2",
  "message": "약관 동의가 필요합니다.",
  "result": {
    "isCompletedSignUp": false,
    "accessToken": null,
    "userId": null,
    "nickname": null,
    "signupToken": "eyJhbGciOiJIUzI1NiJ9.signup...",
    "email": "sniptext@gmail.com",
    "name": "홍길동",
    "profileImageUrl": "https://lh3.googleusercontent.com/a/sample"
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "AUTH401_3",
  "message": "구글 로그인 정보를 확인할 수 없어요. 다시 시도해주세요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| isCompletedSignUp | Boolean | 가입 완료 여부. false면 약관 동의 화면으로 |
| accessToken | String? | 접근 토큰 (30분). 가입 전이면 null |
| userId | Long? | 회원 ID. 가입 전이면 null |
| nickname | String? | 닉네임(구글 이름). 가입 전이면 null |
| signupToken | String? | 약관 동의 API에 넘길 가입용 토큰 (30분). 가입된 회원이면 null |
| email | String? | 구글 이메일 (동의 화면 표시용). 가입된 회원이면 null |
| name | String? | 구글 이름 (동의 화면 표시용). 가입된 회원이면 null |
| profileImageUrl | String? | 구글 프로필 이미지 (동의 화면 표시용) |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 400 | AUTH400_1 | 지원하지 않는 로그인 방식이에요. | provider 값 오류 (S1은 GOOGLE만) |
| 401 | AUTH401_3 | 구글 로그인 정보를 확인할 수 없어요. 다시 시도해주세요. | ID 토큰 서명·발급자·대상·만료 검증 실패, 이메일 미인증 계정 |

##### 비고

- 팀 기존 방식(소셜 ID 토큰을 서버가 검증)을 따릅니다. "가입 필요"도 202가 아니라 **200 + isCompletedSignUp=false**로 구분합니다.
- 서버 검증 항목: 구글 공개키 서명, 발급자(accounts.google.com), 대상(우리 클라이언트 ID), 만료, 이메일 인증 여부.
- 처음 온 사용자는 이 단계에서 회원을 만들지 않습니다. **약관 동의 전에는 개인정보를 저장하지 않기 위해서**입니다. 구글 계정 정보를 담은 가입용 토큰(30분)을 주고, 동의 API에서 회원을 만듭니다.
- 웹: 재발급 토큰은 응답 본문이 아니라 `Set-Cookie`(HttpOnly, Secure, 14일)로 줍니다.
- 로그인 화면은 랜딩(S3)에 생기며, 그 전까지 프론트는 대시보드에 임시 로그인 버튼을 둡니다.
- 개발 환경에서만 쓰는 임시 토큰 발급 경로를 따로 열어, 구글 로그인 완성 전에도 다른 API를 개발할 수 있게 합니다(운영 배포에서는 비활성).
- 약관이 바뀌어 재동의가 필요해지면, 이 응답에 재동의 필요 여부를 알려주는 값을 추가할 예정입니다(약관 변경이 생길 때 추가).

---

### 2. POST /api/v1/auth/signup

| 항목 | 내용 |
| --- | --- |
| 설명 | 약관 동의 후 가입 완료 (첫 로그인 직후 동의 화면) |
| 관련 기능 | 기획 확정: 첫 로그인 직후 필수 약관 동의 1개, 닉네임은 구글 이름 |
| 로그인 | 불필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Content-Type: application/json
```

##### Parameter

없음

##### Body

| 필드 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| signupToken | String | Y | 구글 로그인 API에서 받은 가입용 토큰 |
| termsAgreed | Boolean | Y | 필수 약관(이용약관·개인정보 수집) 동의. true만 허용 |

##### Example

```
POST /api/v1/auth/signup

{
  "signupToken": "eyJhbGciOiJIUzI1NiJ9.signup...",
  "termsAgreed": true
}
```

#### Response

##### 성공 예시 (201, 재발급 토큰은 쿠키로)

```json
{
  "isSuccess": true,
  "code": "AUTH201_1",
  "message": "회원가입이 완료되었습니다.",
  "result": {
    "isCompletedSignUp": true,
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1,
    "nickname": "홍길동"
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "AUTH400_3",
  "message": "약관에 동의해주세요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| isCompletedSignUp | Boolean | true |
| accessToken | String | 접근 토큰 |
| userId | Long | 회원 ID |
| nickname | String | 닉네임 (구글 이름) |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 400 | AUTH400_3 | 약관에 동의해주세요. | 필수 약관(이용약관·개인정보 수집) 미동의 |
| 401 | AUTH401_4 | 가입 시간이 지났어요. 다시 로그인해주세요. | 가입용 토큰 만료(30분) 또는 위조 |
| 409 | AUTH409_1 | 이미 가입된 계정이에요. | 같은 구글 계정으로 가입 완료 요청이 두 번 들어옴 |

##### 비고

- 가입용 토큰 안의 구글 계정 정보로 `users`, `social_accounts`를 만들고, `user_terms_agreements`에 SERVICE(이용약관)·PRIVACY(개인정보 수집·이용) 두 줄을 **현재 약관 버전**으로 저장한 뒤 바로 로그인 처리합니다. 모두 한 트랜잭션입니다.
- 현재 약관 버전은 서버 설정값입니다(예: v1). 약관 본문은 랜딩의 버전별 정적 페이지에 둡니다. 요청에 버전을 보내지 않습니다.
- 닉네임은 구글 이름 그대로 저장합니다(앞뒤 공백 제거, 50자 초과 시 자름). 이름이 비어 있으면 이메일 앞부분을 씁니다. 화면의 말줄임은 DSH-05 기준.
- 마케팅 수신 동의는 받지 않습니다(필요해지면 추가).
- 개인정보처리방침 수집 항목: 구글 이름, 이메일, 프로필 이미지.
- 분석 이벤트 `signup_completed`(signup_method=google)는 화면에서 성공 후 보냅니다.

---

### 3. POST /api/v1/auth/reissue

| 항목 | 내용 |
| --- | --- |
| 설명 | 접근 토큰 재발급 (재발급 토큰 교체) |
| 관련 기능 | PRD 8.2(재발급 토큰 교체 방식) |
| 로그인 | 불필요 (재발급 토큰 쿠키 사용) |
| 캐시 | no-store |

#### Request

##### Header

```
Cookie: refreshToken=...
Content-Type: application/json
```

##### Parameter

없음

##### Body

없음

##### Example

```
POST /api/v1/auth/reissue
Cookie: refreshToken=...
```

#### Response

##### 성공 예시 (새 재발급 토큰은 쿠키로 교체)

```json
{
  "isSuccess": true,
  "code": "AUTH200_3",
  "message": "토큰이 재발급되었습니다.",
  "result": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "AUTH401_5",
  "message": "다시 로그인해주세요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| accessToken | String | 새 접근 토큰 (30분) |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 401 | AUTH401_5 | 다시 로그인해주세요. | 재발급 토큰 없음·만료·폐기, 이미 쓴 토큰 재사용 감지(계보 전체 폐기) |

##### 비고

- 재발급할 때마다 재발급 토큰도 새것으로 바꾸고 이전 것은 폐기합니다.
- 이미 폐기된 재발급 토큰이 다시 오면 탈취로 보고 그 계보 전체를 폐기합니다 → 다시 로그인.
- 프론트는 새로고침 직후(메모리의 접근 토큰이 사라졌을 때)와 COMMON401_1을 받았을 때 이 API를 부릅니다.

---

### 4. POST /api/v1/auth/logout

| 항목 | 내용 |
| --- | --- |
| 설명 | 로그아웃 (웹·확장 세션 모두 무효화) |
| 관련 기능 | DSH-07 |
| 로그인 | 불필요 (재발급 토큰 쿠키 사용) |
| 캐시 | no-store |

#### Request

##### Header

```
Cookie: refreshToken=...
Content-Type: application/json
```

##### Parameter

없음

##### Body

없음

##### Example

```
POST /api/v1/auth/logout
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "AUTH200_1",
  "message": "로그아웃되었습니다.",
  "result": null
}
```

##### Error Code

이 API 전용 에러 없음 (공통 에러만)

##### 비고

- 해당 회원의 재발급 토큰을 **웹·확장 모두** 폐기하고, 재발급 토큰 쿠키를 지웁니다.
- 같은 브라우저의 확장은 웹이 확장에 로그아웃 메시지를 직접 보내 즉시 끊습니다 (S2).
- 다른 기기의 확장은 접근 토큰 만료(30분)까지 유지될 수 있습니다. (팀 공유 완료)
- **접근 토큰 없이 재발급 토큰 쿠키만으로 처리합니다.** 접근 토큰을 요구하면 만료된 상태에서 로그아웃이 401로 막히기 때문입니다. 쿠키가 없거나 이미 폐기됐어도 성공으로 응답합니다.

---


## 회원

### 5. GET /api/v1/users/me

| 항목 | 내용 |
| --- | --- |
| 설명 | 내 정보 조회 (사이드바 프로필, 계정 팝오버) |
| 관련 기능 | DSH-05, DSH-06, DSH-07(계정 정보 카드), MYT-03(정렬 기억 불러오기) |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

없음

##### Body

없음

##### Example

```
GET /api/v1/users/me
```

#### Response

##### 성공 예시 (구글 로그인 사용자)

```json
{
  "isSuccess": true,
  "code": "USER200_1",
  "message": "내 정보 조회에 성공했습니다.",
  "result": {
    "userId": 1,
    "nickname": "스닙이",
    "email": "sniptext@naver.com",
    "profileImageUrl": "https://lh3.googleusercontent.com/a/sample",
    "loginType": "GOOGLE",
    "templateSort": "RECENT_USED"
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "USER404_1",
  "message": "회원 정보를 찾을 수 없어요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| userId | Long | 회원 ID |
| nickname | String | 닉네임 |
| email | String | 이메일 |
| profileImageUrl | String | 프로필 이미지 주소. null이면 닉네임 첫 글자 원형 아바타 |
| loginType | String | GOOGLE |
| templateSort | String | 저장된 내 템플릿 정렬 기준 |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 404 | USER404_1 | 회원 정보를 찾을 수 없어요. | 탈퇴했거나 없는 회원의 토큰 |

##### 비고

- 닉네임·이메일 말줄임은 화면에서 처리합니다.
- 플랜·사용량은 자주 바뀌므로 이 API에 넣지 않고 `GET /api/v1/users/me/usage`로 분리했습니다.

---

### 6. GET /api/v1/users/me/usage

| 항목 | 내용 |
| --- | --- |
| 설명 | 플랜 사용량 조회 (사이드바 개수 뱃지, 플랜 카드, AI 남은 횟수) |
| 관련 기능 | DSH-02, DSH-03, DSH-04, MYT-01(클릭 시 서버 재검증), AIT-01(버튼 라벨), COM-03, COM-04 |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

없음

##### Body

없음

##### Example

```
GET /api/v1/users/me/usage
```

#### Response

##### 성공 예시 (Free, 3/5)

```json
{
  "isSuccess": true,
  "code": "USER200_2",
  "message": "플랜 사용량 조회에 성공했습니다.",
  "result": {
    "plan": "FREE",
    "template": {
      "count": 3,
      "limit": 5,
      "remaining": 2,
      "isFull": false
    },
    "aiTone": {
      "available": false,
      "usedCount": 0,
      "limit": 0,
      "remaining": 0,
      "resetAt": "2026-10-12T00:00:00+09:00"
    }
  }
}
```

##### 성공 예시 (Free, 5/5 한도 도달)

```json
{
  "isSuccess": true,
  "code": "USER200_2",
  "message": "플랜 사용량 조회에 성공했습니다.",
  "result": {
    "plan": "FREE",
    "template": {
      "count": 5,
      "limit": 5,
      "remaining": 0,
      "isFull": true
    },
    "aiTone": {
      "available": false,
      "usedCount": 0,
      "limit": 0,
      "remaining": 0,
      "resetAt": "2026-10-12T00:00:00+09:00"
    }
  }
}
```

##### 성공 예시 (Pro, AI 4회 사용)

```json
{
  "isSuccess": true,
  "code": "USER200_2",
  "message": "플랜 사용량 조회에 성공했습니다.",
  "result": {
    "plan": "PRO",
    "template": {
      "count": 12,
      "limit": 50,
      "remaining": 38,
      "isFull": false
    },
    "aiTone": {
      "available": true,
      "usedCount": 4,
      "limit": 10,
      "remaining": 6,
      "resetAt": "2026-10-12T00:00:00+09:00"
    }
  }
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| plan | String | FREE / PRO |
| template.count | Integer | 보유 템플릿 수 (휴지통 제외) → 사이드바 뱃지·"3 / 5" |
| template.limit | Integer | 플랜 한도 (Free 5 / Pro 50) |
| template.remaining | Integer | 더 만들 수 있는 개수 → "템플릿을 N개 더 만들 수 있어요." |
| template.isFull | Boolean | 한도 도달 여부 → 강조색, 한도 모달 판단 |
| aiTone.available | Boolean | AI 톤 교정 사용 가능 여부 (false면 업그레이드 유도 화면) |
| aiTone.usedCount | Integer | 오늘 사용 횟수 |
| aiTone.limit | Integer | 일일 한도 (Pro 10) |
| aiTone.remaining | Integer | 오늘 남은 횟수 |
| aiTone.resetAt | String | 다음 리셋 시각 (다음 날 00:00 KST) → 카운트다운 |

##### Error Code

이 API 전용 에러 없음 (공통 에러만)

##### 비고

- **결제 도입 전 플랜 판정**: 회원의 `plan_override` 값(테스트 계정을 Pro로 지정할 때 사용), 없으면 FREE. 결제가 들어오면 이 판정 로직 안쪽만 바뀌고 응답 형태는 그대로입니다.
- 템플릿 생성·삭제·복구·갤러리 저장 성공 후 이 API를 다시 불러 사이드바·플랜 카드를 갱신합니다 (COM-04).
- MYT-01 "새 템플릿" 클릭 시 이 API로 한도를 재검증합니다. 최종 검증은 생성 API가 다시 합니다.
- 조회 실패 시 화면은 Free로 간주합니다 (DSH-03).

---

### 7. PATCH /api/v1/users/me/settings

| 항목 | 내용 |
| --- | --- |
| 설명 | 내 템플릿 정렬 기준 저장 (다음 접속 시 유지) |
| 관련 기능 | MYT-03, 정책표 11(정렬 기억, 계정 단위) |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

없음

##### Body

| 필드 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| templateSort | String | Y | RECENT_USED(최근 사용순) / RECENT_EDITED(최근 수정순) |

##### Example

```
PATCH /api/v1/users/me/settings

{
  "templateSort": "RECENT_EDITED"
}
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "USER200_3",
  "message": "설정이 저장되었습니다.",
  "result": {
    "templateSort": "RECENT_EDITED"
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "USER400_1",
  "message": "지원하지 않는 정렬 기준이에요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| templateSort | String | 저장된 정렬 기준 |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 400 | USER400_1 | 지원하지 않는 정렬 기준이에요. | templateSort 값 오류 |

##### 비고

- 정렬 변경 시 화면은 즉시 다시 정렬하고, 이 요청은 뒤에서 보냅니다. 실패해도 사용자에게 알리지 않습니다.

---


## 내 템플릿

### 8. GET /api/v1/templates

| 항목 | 내용 |
| --- | --- |
| 설명 | 내 템플릿 목록 조회 (휴지통 제외 전체) |
| 관련 기능 | MYT-03, MYT-04, MYT-05, DSH-09(상단 통합 검색) |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| sort | String | N | 쿼리 파라미터. RECENT_USED / RECENT_EDITED. 없으면 저장된 정렬 기준 |

##### Body

없음

##### Example

```
GET /api/v1/templates?sort=RECENT_USED
```

#### Response

##### 성공 예시 (최근 사용순)

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_1",
  "message": "템플릿 목록 조회에 성공했습니다.",
  "result": {
    "sort": "RECENT_USED",
    "totalCount": 3,
    "templates": [
      {
        "templateId": 12,
        "title": "프로젝트 견적 안내",
        "shortcut": "/견적",
        "content": "안녕하세요 {{고객명}}님,\n문의 주신 프로젝트 견적은 {{금액}}원입니다.\n{{고객명}}님의 일정에 맞춰 진행 가능합니다.",
        "variables": [
          "고객명",
          "금액"
        ],
        "lastUsedAt": "2026-10-07T09:12:00+09:00",
        "createdAt": "2026-09-20T14:03:00+09:00",
        "editedAt": "2026-10-01T11:40:00+09:00"
      },
      {
        "templateId": 15,
        "title": "택배 주소",
        "shortcut": "/주소",
        "content": "서울특별시 마포구 와우산로 94 (우) 04066",
        "variables": [],
        "lastUsedAt": "2026-10-06T18:30:00+09:00",
        "createdAt": "2026-10-02T10:00:00+09:00",
        "editedAt": "2026-10-02T10:00:00+09:00"
      },
      {
        "templateId": 18,
        "title": "주간 회의 안내",
        "shortcut": "/회의",
        "content": "{{날짜}} {{시간}}에 주간 회의가 있습니다. 안건은 {{안건}}입니다.",
        "variables": [
          "날짜",
          "시간",
          "안건"
        ],
        "lastUsedAt": null,
        "createdAt": "2026-10-05T09:00:00+09:00",
        "editedAt": "2026-10-05T09:00:00+09:00"
      }
    ]
  }
}
```

##### 성공 예시 (템플릿 0개 → 4-1 빈 화면)

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_1",
  "message": "템플릿 목록 조회에 성공했습니다.",
  "result": {
    "sort": "RECENT_USED",
    "totalCount": 0,
    "templates": []
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "TEMPLATE400_8",
  "message": "지원하지 않는 정렬 기준이에요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| sort | String | 적용된 정렬 기준 |
| totalCount | Integer | 템플릿 수 |
| templates[].templateId | Long | 템플릿 ID |
| templates[].title | String | 템플릿 이름 |
| templates[].shortcut | String | 호출 단축어 ("/" 포함, 입력한 대소문자 그대로) |
| templates[].content | String | 본문 전체 (줄바꿈 포함). 카드에는 표시하지 않음 → 통합 검색·복사·미리보기 패널에서 사용 |
| templates[].variables | String[] | 본문의 변수 이름 (첫 등장 순, 중복 제거) → 카드의 변수명 표시(MYT-05) |
| templates[].lastUsedAt | String | 최근 사용 시각, 사용 이력 없으면 null. 카드에는 표시하지 않음 → 최근 사용순 정렬 기준 |
| templates[].createdAt | String | 생성 시각 |
| templates[].editedAt | String | 마지막 수정 시각 (최근 수정순 기준) |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 400 | TEMPLATE400_8 | 지원하지 않는 정렬 기준이에요. | sort 값 오류 |

##### 비고

- **페이지 나눔 없이 전체를 내려줍니다.** 최대 50개(Pro)라 한 번에 받는 게 빠르고, 본문 암호화 때문에 서버에서 본문 검색을 하지 않습니다.
- **검색은 상단 통합 검색(DSH-09)에서 화면이 처리합니다.** 이 목록과 갤러리 전체 조회 결과를 합쳐 검색하며, 규칙은 공통 규칙 11장을 따릅니다. (MYT-02 내 템플릿 검색창은 삭제됨)
- **카드(MYT-05)에는 단축어·제목·변수명만 표시합니다.** 본문(content)은 카드에 보이지 않지만 통합 검색·복사·미리보기 패널에 필요해서 그대로 내려줍니다. 최근 사용 시각(lastUsedAt)도 카드에 표시하지 않고 정렬 기준으로만 씁니다.
- 정렬 기준
    - RECENT_USED: 최근 사용 시각 내림차순 → 사용 이력 없는 템플릿은 그 뒤에 생성 시각 내림차순
    - RECENT_EDITED: 수정 시각(editedAt) 내림차순. 복사·사용은 수정으로 치지 않음
- `sort`를 보내도 저장된 정렬 기준은 바뀌지 않습니다. 저장은 `PATCH /api/v1/users/me/settings`.

---

### 9. GET /api/v1/templates/{templateId}

| 항목 | 내용 |
| --- | --- |
| 설명 | 내 템플릿 상세 조회 (미리보기 패널, 편집 모드 진입) |
| 관련 기능 | MYT-09, NEW-07 |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| templateId | Long | Y | 경로 변수. 템플릿 ID |

##### Body

없음

##### Example

```
GET /api/v1/templates/12
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_2",
  "message": "템플릿 조회에 성공했습니다.",
  "result": {
    "templateId": 12,
    "title": "프로젝트 견적 안내",
    "shortcut": "/견적",
    "content": "안녕하세요 {{고객명}}님,\n문의 주신 프로젝트 견적은 {{금액}}원입니다.\n{{고객명}}님의 일정에 맞춰 진행 가능합니다.",
    "variables": [
      "고객명",
      "금액"
    ],
    "lastUsedAt": "2026-10-07T09:12:00+09:00",
    "createdAt": "2026-09-20T14:03:00+09:00",
    "editedAt": "2026-10-01T11:40:00+09:00"
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "TEMPLATE404_1",
  "message": "템플릿을 찾을 수 없어요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| templateId | Long | 템플릿 ID |
| title | String | 템플릿 이름 |
| shortcut | String | 호출 단축어 ("/" 포함, 입력한 대소문자 그대로) |
| content | String | 본문 전체 (줄바꿈 포함). 카드에는 표시하지 않음 → 통합 검색·복사·미리보기 패널에서 사용 |
| variables | String[] | 본문의 변수 이름 (첫 등장 순, 중복 제거) → 카드의 변수명 표시(MYT-05) |
| lastUsedAt | String | 최근 사용 시각, 사용 이력 없으면 null. 카드에는 표시하지 않음 → 최근 사용순 정렬 기준 |
| createdAt | String | 생성 시각 |
| editedAt | String | 마지막 수정 시각 (최근 수정순 기준) |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 404 | TEMPLATE404_1 | 템플릿을 찾을 수 없어요. | 없는 템플릿, 다른 사람 템플릿, 휴지통에 있는 템플릿 |
| 404 | TEMPLATE404_2 | 이미 삭제된 템플릿이에요. | 휴지통으로 이동된 템플릿에 수정·조회·사용 기록 요청 |

##### 비고

- 미리보기 패널은 목록 데이터로 바로 그려도 됩니다. 편집 화면을 주소로 직접 열거나 새로고침했을 때 이 API를 씁니다.
- 휴지통에 있는 템플릿이면 TEMPLATE404_2("이미 삭제된 템플릿이에요.")를 돌려줍니다. 편집 중 다른 곳에서 삭제된 경우에 화면이 이 문구를 띄웁니다.

---

### 10. POST /api/v1/templates

| 항목 | 내용 |
| --- | --- |
| 설명 | 새 템플릿 저장 |
| 관련 기능 | NEW-02, NEW-03, NEW-04, NEW-05, MYT-01, 정책표 2·4·5·6 |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

없음

##### Body

| 필드 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| title | String | Y | 템플릿 이름. 앞뒤 공백 제거 후 1~50자, 이름 중복 허용 |
| shortcut | String | Y | 호출 단축어. "/" 포함, "/" 뒤 한글(완성형·자모)·영문·숫자 2~10자 |
| content | String | Y | 본문. 일반 텍스트(줄바꿈 유지), 1~3000자. 변수는 {{변수명}} |

##### Example

```
POST /api/v1/templates

{
  "title": "프로젝트 견적 안내",
  "shortcut": "/견적",
  "content": "안녕하세요 {{고객명}}님,\n문의 주신 프로젝트 견적은 {{금액}}원입니다.\n{{고객명}}님의 일정에 맞춰 진행 가능합니다."
}
```

#### Response

##### 성공 예시 (201)

```json
{
  "isSuccess": true,
  "code": "TEMPLATE201_1",
  "message": "템플릿이 저장되었습니다.",
  "result": {
    "templateId": 12,
    "title": "프로젝트 견적 안내",
    "shortcut": "/견적",
    "content": "안녕하세요 {{고객명}}님,\n문의 주신 프로젝트 견적은 {{금액}}원입니다.\n{{고객명}}님의 일정에 맞춰 진행 가능합니다.",
    "variables": [
      "고객명",
      "금액"
    ],
    "lastUsedAt": null,
    "createdAt": "2026-10-07T10:30:00+09:00",
    "editedAt": "2026-10-07T10:30:00+09:00"
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "TEMPLATE409_1",
  "message": "이미 사용 중인 단축어예요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| templateId | Long | 템플릿 ID |
| title | String | 템플릿 이름 |
| shortcut | String | 호출 단축어 ("/" 포함, 입력한 대소문자 그대로) |
| content | String | 본문 전체 (줄바꿈 포함). 카드에는 표시하지 않음 → 통합 검색·복사·미리보기 패널에서 사용 |
| variables | String[] | 본문의 변수 이름 (첫 등장 순, 중복 제거) → 카드의 변수명 표시(MYT-05) |
| lastUsedAt | String | 최근 사용 시각, 사용 이력 없으면 null. 카드에는 표시하지 않음 → 최근 사용순 정렬 기준 |
| createdAt | String | 생성 시각 |
| editedAt | String | 마지막 수정 시각 (최근 수정순 기준) |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 400 | TEMPLATE400_1 | 템플릿 이름을 입력해주세요. | 이름이 비었거나 공백만 입력 |
| 400 | TEMPLATE400_2 | 템플릿 이름은 50자까지 입력할 수 있어요. | 이름 50자 초과 |
| 400 | TEMPLATE400_3 | 호출 단축어를 입력해주세요. | 단축어가 비었거나 "/"만 있음 |
| 400 | TEMPLATE400_4 | 한글·영문·숫자 2~10자로 입력해주세요. | 단축어 형식 불일치 (공백·특수문자·이모지, 길이) |
| 400 | TEMPLATE400_5 | 사용할 수 없는 단축어예요. | 예약어 사용 (/톤교정 등) |
| 400 | TEMPLATE400_6 | 템플릿 내용을 입력해주세요. | 본문이 비었거나 공백만 입력 |
| 400 | TEMPLATE400_7 | 템플릿 내용은 3,000자까지 입력할 수 있어요. | 본문 3000자 초과 |
| 403 | TEMPLATE403_1 | 템플릿 저장 한도에 도달했어요. | 활성 템플릿 수가 플랜 한도(Free 5 / Pro 50) 이상. 화면은 한도 모달 노출 |
| 409 | TEMPLATE409_1 | 이미 사용 중인 단축어예요. | 내 템플릿(휴지통 제외)에 같은 단축어 존재. 대소문자 무시 |

##### 비고

- **검증 순서는 이름 → 단축어 → 내용**이며, 여러 개가 틀려도 첫 번째 에러 하나만 내려줍니다 (NEW-02와 동일).
- 에러 코드별 화면 처리: 이름(400_1·2)·단축어(400_3·4·5, 409_1)·내용(400_6·7)은 해당 필드 에러 + 포커스, 403_1은 한도 모달.
- 단축어 중복 비교는 대소문자 무시 + 유니코드 정규화 후 비교. 휴지통 템플릿의 단축어는 비교 대상에서 제외.
- 형식이 맞지 않는 변수(`{{고객 명}}`, `{{고객명`)는 **저장을 막지 않고** 일반 글자로 취급합니다. 경고 문구는 화면에서 표시.
- 한도 검사와 저장은 한 트랜잭션에서 회원 행을 잠그고 처리해, 두 탭에서 동시에 저장해도 한도를 넘지 않게 합니다.
- 본문의 줄바꿈 문자를 한 가지로 통일한 뒤 글자 수를 셉니다. 글자 수 기준은 화면 입력창과 동일하게 맞춥니다(공통 규칙 5장).

---

### 11. PUT /api/v1/templates/{templateId}

| 항목 | 내용 |
| --- | --- |
| 설명 | 템플릿 수정 (편집 모드 저장) |
| 관련 기능 | NEW-07, MYT-07, MYT-09, 정책표 4(자기 자신 단축어는 중복 아님) |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| templateId | Long | Y | 경로 변수. 템플릿 ID |

##### Body

| 필드 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| title | String | Y | 템플릿 이름. 앞뒤 공백 제거 후 1~50자, 이름 중복 허용 |
| shortcut | String | Y | 호출 단축어. "/" 포함, "/" 뒤 한글(완성형·자모)·영문·숫자 2~10자 |
| content | String | Y | 본문. 일반 텍스트(줄바꿈 유지), 1~3000자. 변수는 {{변수명}} |

##### Example

```
PUT /api/v1/templates/12

{
  "title": "프로젝트 견적 안내 (수정)",
  "shortcut": "/견적",
  "content": "안녕하세요 {{고객명}}님,\n문의 주신 프로젝트 견적은 {{금액}}원입니다.\n{{고객명}}님의 일정에 맞춰 진행 가능합니다."
}
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_3",
  "message": "수정 내용이 저장되었습니다.",
  "result": {
    "templateId": 12,
    "title": "프로젝트 견적 안내 (수정)",
    "shortcut": "/견적",
    "content": "안녕하세요 {{고객명}}님,\n문의 주신 프로젝트 견적은 {{금액}}원입니다.\n{{고객명}}님의 일정에 맞춰 진행 가능합니다.",
    "variables": [
      "고객명",
      "금액"
    ],
    "lastUsedAt": "2026-10-07T09:12:00+09:00",
    "createdAt": "2026-09-20T14:03:00+09:00",
    "editedAt": "2026-10-07T10:45:00+09:00"
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "TEMPLATE404_1",
  "message": "템플릿을 찾을 수 없어요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| templateId | Long | 템플릿 ID |
| title | String | 템플릿 이름 |
| shortcut | String | 호출 단축어 ("/" 포함, 입력한 대소문자 그대로) |
| content | String | 본문 전체 (줄바꿈 포함). 카드에는 표시하지 않음 → 통합 검색·복사·미리보기 패널에서 사용 |
| variables | String[] | 본문의 변수 이름 (첫 등장 순, 중복 제거) → 카드의 변수명 표시(MYT-05) |
| lastUsedAt | String | 최근 사용 시각, 사용 이력 없으면 null. 카드에는 표시하지 않음 → 최근 사용순 정렬 기준 |
| createdAt | String | 생성 시각 |
| editedAt | String | 마지막 수정 시각 (최근 수정순 기준) |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 400 | TEMPLATE400_1 | 템플릿 이름을 입력해주세요. | 이름이 비었거나 공백만 입력 |
| 400 | TEMPLATE400_2 | 템플릿 이름은 50자까지 입력할 수 있어요. | 이름 50자 초과 |
| 400 | TEMPLATE400_3 | 호출 단축어를 입력해주세요. | 단축어가 비었거나 "/"만 있음 |
| 400 | TEMPLATE400_4 | 한글·영문·숫자 2~10자로 입력해주세요. | 단축어 형식 불일치 (공백·특수문자·이모지, 길이) |
| 400 | TEMPLATE400_5 | 사용할 수 없는 단축어예요. | 예약어 사용 (/톤교정 등) |
| 400 | TEMPLATE400_6 | 템플릿 내용을 입력해주세요. | 본문이 비었거나 공백만 입력 |
| 400 | TEMPLATE400_7 | 템플릿 내용은 3,000자까지 입력할 수 있어요. | 본문 3000자 초과 |
| 404 | TEMPLATE404_1 | 템플릿을 찾을 수 없어요. | 없는 템플릿, 다른 사람 템플릿, 휴지통에 있는 템플릿 |
| 409 | TEMPLATE409_1 | 이미 사용 중인 단축어예요. | 내 템플릿(휴지통 제외)에 같은 단축어 존재. 대소문자 무시 |
| 404 | TEMPLATE404_2 | 이미 삭제된 템플릿이에요. | 휴지통으로 이동된 템플릿에 수정·조회·사용 기록 요청 |

##### 비고

- 세 필드를 모두 보내는 전체 수정입니다. 바뀌지 않은 필드도 그대로 보내주세요.
- 세 필드가 모두 기존과 같으면(이름·본문 정리 후 비교) 아무것도 바뀌지 않아 editedAt도 그대로입니다. 편집 화면을 열었다가 그대로 저장해도 최근 수정순이 바뀌지 않습니다. 단축어는 대소문자만 바꿔도 수정으로 봅니다.
- 자기 자신의 단축어는 중복으로 보지 않습니다.
- 동시에 두 곳에서 수정하면 나중 저장이 덮어씁니다.
- 단축어 변경은 확장에 즉시 반영돼야 하므로(정책표 4) S2에서 웹이 확장에 변경 알림을 보냅니다.
- 편집 중 다른 곳에서 휴지통으로 옮겨졌다면 TEMPLATE404_2를 돌려줍니다.

---

### 12. DELETE /api/v1/templates/{templateId}

| 항목 | 내용 |
| --- | --- |
| 설명 | 템플릿 삭제 → 휴지통 이동 (소프트 삭제) |
| 관련 기능 | MYT-07, MYT-08, 정책표 7 |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| templateId | Long | Y | 경로 변수. 템플릿 ID |

##### Body

없음

##### Example

```
DELETE /api/v1/templates/12
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_4",
  "message": "템플릿을 휴지통으로 옮겼습니다.",
  "result": {
    "templateId": 12,
    "title": "프로젝트 견적 안내",
    "deletedAt": "2026-10-07T10:50:00+09:00",
    "purgeAt": "2026-11-06T10:50:00+09:00"
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "TEMPLATE404_1",
  "message": "템플릿을 찾을 수 없어요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| templateId | Long | 템플릿 ID |
| title | String | 삭제 토스트용 이름 |
| deletedAt | String | 휴지통 이동 시각 |
| purgeAt | String | 자동 영구 삭제 예정 시각 (+30일) |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 404 | TEMPLATE404_1 | 템플릿을 찾을 수 없어요. | 없는 템플릿, 다른 사람 템플릿, 휴지통에 있는 템플릿 |

##### 비고

- 삭제 즉시 단축어 점유가 풀려 다른 템플릿이 같은 단축어를 쓸 수 있습니다.
- 활성 개수가 1 줄어듭니다. 성공 후 사용량 API로 사이드바 갱신.

---

### 13. GET /api/v1/templates/shortcuts/availability

| 항목 | 내용 |
| --- | --- |
| 설명 | 단축어 중복·형식 실시간 확인 (입력 정지 0.5초 후) |
| 관련 기능 | NEW-04, 정책표 4 |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| shortcut | String | Y | 확인할 단축어. "/" 포함, 주소에 넣을 때 인코딩 |
| excludeTemplateId | Long | N | 편집 모드일 때 자기 자신 템플릿 ID (중복 판정에서 제외) |

##### Body

없음

##### Example

```
GET /api/v1/templates/shortcuts/availability?shortcut=%2F%EA%B2%AC%EC%A0%81&excludeTemplateId=12

(인코딩 전: shortcut=/견적)
```

#### Response

##### 성공 예시 (사용 가능)

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_5",
  "message": "단축어 확인에 성공했습니다.",
  "result": {
    "shortcut": "/견적서",
    "available": true,
    "reason": null
  }
}
```

##### 성공 예시 (중복)

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_5",
  "message": "단축어 확인에 성공했습니다.",
  "result": {
    "shortcut": "/견적",
    "available": false,
    "reason": "DUPLICATED"
  }
}
```

##### 성공 예시 (형식 오류)

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_5",
  "message": "단축어 확인에 성공했습니다.",
  "result": {
    "shortcut": "/a",
    "available": false,
    "reason": "INVALID_FORMAT"
  }
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| shortcut | String | 확인한 단축어 |
| available | Boolean | 사용 가능 여부 |
| reason | String | 사용 불가 이유. INVALID_FORMAT → "한글·영문·숫자 2~10자로 입력해주세요." / RESERVED → "사용할 수 없는 단축어예요." / DUPLICATED → "이미 사용 중인 단축어예요." |

##### Error Code

이 API 전용 에러 없음 (공통 에러만)

##### 비고

- 입력 확인용이라 형식 오류도 에러가 아닌 200 + reason으로 내려줍니다. 화면은 reason에 맞는 문구를 필드 아래에 표시.
- 한글 입력 조합 중에는 호출하지 않고, 조합이 끝난 뒤 0.5초 입력이 멈추면 호출합니다.
- 여기서 통과해도 저장 순간 다른 곳에서 먼저 등록할 수 있어, 저장 API가 최종 검증합니다 (409_1).
- `shortcut` 파라미터 자체가 없으면 `COMMON400_1`입니다. `?shortcut=`이나 `?shortcut=/`처럼 값이 비었으면 에러가 아닌 200, `available: false`, `reason: INVALID_FORMAT`으로 응답합니다. 화면은 `/`만 있는 처음 상태에서 호출하지 않지만, 화면을 거치지 않은 요청도 같은 규칙으로 처리합니다. 빈 값 안내("호출 단축어를 입력해주세요.")는 저장 시점에 `TEMPLATE400_3`으로 합니다.

---

### 14. POST /api/v1/templates/{templateId}/usages

| 항목 | 내용 |
| --- | --- |
| 설명 | 템플릿 사용 기록 (복사·삽입 성공 시 최근 사용 시각 갱신) |
| 관련 기능 | MYT-06, MYT-09, MYT-10, 정책표 11(최근 사용 시각 갱신) |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| templateId | Long | Y | 경로 변수. 템플릿 ID |

##### Body

| 필드 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| channel | String | Y | DASHBOARD_COPY(대시보드 복사) / POPUP_COPY(확장 팝업 복사) / EMAIL_INLINE(메일 인라인 삽입) |

##### Example

```
POST /api/v1/templates/12/usages

{
  "channel": "DASHBOARD_COPY"
}
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_6",
  "message": "사용 기록이 저장되었습니다.",
  "result": {
    "templateId": 12,
    "lastUsedAt": "2026-10-11T20:15:00+09:00"
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "TEMPLATE404_1",
  "message": "템플릿을 찾을 수 없어요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| templateId | Long | 템플릿 ID |
| lastUsedAt | String | 최근 사용 시각, 사용 이력 없으면 null. 카드에는 표시하지 않음 → 최근 사용순 정렬 기준 |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 400 | TEMPLATE400_9 | 지원하지 않는 사용 채널이에요. | channel 값 오류 |
| 404 | TEMPLATE404_1 | 템플릿을 찾을 수 없어요. | 없는 템플릿, 다른 사람 템플릿, 휴지통에 있는 템플릿 |
| 404 | TEMPLATE404_2 | 이미 삭제된 템플릿이에요. | 휴지통으로 이동된 템플릿에 수정·조회·사용 기록 요청 |

##### 비고

- **사용 횟수는 저장하지 않습니다(기획 확정).** 최근 사용순 정렬과 확장 팝업의 "최근 사용 5개"를 위해 최근 사용 시각만 갱신합니다.
- **클립보드 복사가 성공한 뒤에만** 호출합니다. 이 요청이 실패해도 사용자에게 알리지 않습니다.
- 채널별 사용 통계는 분석 도구 이벤트 `template_used`(channel 속성)로 봅니다. 화면에서 따로 보냅니다.
- editedAt은 바뀌지 않아 최근 수정순 정렬에 영향이 없습니다.

---


## 휴지통

### 15. GET /api/v1/trash/templates

| 항목 | 내용 |
| --- | --- |
| 설명 | 삭제된 템플릿 목록 조회 (최근 삭제순) |
| 관련 기능 | BIN-02, BIN-04, 정책표 7 |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| cursor | String | N | 다음 페이지 커서. 첫 요청은 비움 |
| size | Integer | N | 한 번에 받을 개수. 기본 20, 최대 50 |

##### Body

없음

##### Example

```
GET /api/v1/trash/templates?size=20
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "TRASH200_1",
  "message": "휴지통 조회에 성공했습니다.",
  "result": {
    "totalCount": 2,
    "templates": [
      {
        "templateId": 18,
        "title": "주간 회의 안내",
        "shortcut": "/회의",
        "deletedAt": "2026-10-07T10:58:00+09:00",
        "purgeAt": "2026-11-06T10:58:00+09:00"
      },
      {
        "templateId": 9,
        "title": "환불 안내",
        "shortcut": "/환불",
        "deletedAt": "2026-10-01T16:20:00+09:00",
        "purgeAt": "2026-10-31T16:20:00+09:00"
      }
    ],
    "hasNext": false,
    "nextCursor": null
  }
}
```

##### 성공 예시 (비어 있음 → 4번 빈 화면, 비우기 버튼 비활성)

```json
{
  "isSuccess": true,
  "code": "TRASH200_1",
  "message": "휴지통 조회에 성공했습니다.",
  "result": {
    "totalCount": 0,
    "templates": [],
    "hasNext": false,
    "nextCursor": null
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "TRASH400_1",
  "message": "잘못된 페이지 요청이에요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| totalCount | Integer | 휴지통 전체 개수 (비우기 버튼 활성 판단) |
| templates[].templateId | Long | 템플릿 ID |
| templates[].title | String | 템플릿 이름 |
| templates[].shortcut | String | 삭제 당시 단축어 (BIN-04 칩) |
| templates[].deletedAt | String | 삭제 시각 → "방금 전" 등 상대 표기 |
| templates[].purgeAt | String | 자동 영구 삭제 예정 시각 |
| hasNext | Boolean | 다음 페이지 존재 여부 |
| nextCursor | String | 다음 요청에 넣을 커서. 마지막이면 null |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 400 | TRASH400_1 | 잘못된 페이지 요청이에요. | cursor 값 오류 또는 size 범위(1~50) 초과 |

##### 비고

- 휴지통은 개수 제한이 없어(30일간 계속 쌓임) 무한 스크롤용 커서 방식으로 나눕니다.
- 커서 방식이라 중간에 복구·삭제가 일어나도 항목이 밀리거나 중복되지 않습니다.

---

### 16. POST /api/v1/trash/templates/{templateId}/restore

| 항목 | 내용 |
| --- | --- |
| 설명 | 휴지통 템플릿 복구 (내 템플릿으로 복원, 개수 +1) |
| 관련 기능 | BIN-05, BIN-07, 정책표 2·4·7 |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| templateId | Long | Y | 경로 변수. 템플릿 ID |

##### Body

없음

##### Example

```
POST /api/v1/trash/templates/18/restore
```

#### Response

##### 성공 예시 (단축어 그대로)

```json
{
  "isSuccess": true,
  "code": "TRASH200_2",
  "message": "템플릿을 복구했습니다.",
  "result": {
    "templateId": 18,
    "title": "주간 회의 안내",
    "shortcut": "/회의",
    "originalShortcut": "/회의",
    "shortcutChanged": false
  }
}
```

##### 성공 예시 (단축어 충돌 → 자동 번호)

```json
{
  "isSuccess": true,
  "code": "TRASH200_2",
  "message": "템플릿을 복구했습니다.",
  "result": {
    "templateId": 18,
    "title": "주간 회의 안내",
    "shortcut": "/회의2",
    "originalShortcut": "/회의",
    "shortcutChanged": true
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "TEMPLATE403_1",
  "message": "템플릿 저장 한도에 도달했어요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| templateId | Long | 템플릿 ID |
| title | String | 복구 토스트용 이름 |
| shortcut | String | 복구 후 최종 단축어 |
| originalShortcut | String | 삭제 당시 단축어 |
| shortcutChanged | Boolean | 자동 번호가 붙었는지 → 토스트 안내 |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 403 | TEMPLATE403_1 | 템플릿 저장 한도에 도달했어요. | 활성 템플릿 수가 플랜 한도(Free 5 / Pro 50) 이상. 화면은 한도 모달 노출 |
| 409 | TEMPLATE409_2 | 사용할 수 있는 단축어 번호가 없어요. | 자동 번호 2~99가 모두 사용 중 |
| 404 | TRASH404_1 | 휴지통에서 템플릿을 찾을 수 없어요. | 휴지통에 없는 템플릿(이미 복구·영구 삭제됨, 다른 사람 템플릿) |

##### 비고

- 한도 도달(Free 5/5) 시 복구하지 않고 TEMPLATE403_1 → BIN-07 한도 모달.
- 자동 번호 규칙: 원본 뒤에 2부터 99까지 붙여 비어 있는 첫 번호 사용. "/" 뒤가 10자를 넘으면 원본을 뒤에서 잘라 맞춤. 예) /견적요청서작성안내문 → /견적요청서작성안내2
- 숫자로 끝나는 단축어(/견적2)는 원본 뒤에 붙입니다(/견적22). (개발팀 결정)
- editedAt·최근 사용 시각은 삭제 전 값 그대로 돌아옵니다.

---

### 17. DELETE /api/v1/trash/templates/{templateId}

| 항목 | 내용 |
| --- | --- |
| 설명 | 휴지통 템플릿 개별 영구 삭제 |
| 관련 기능 | BIN-06, 정책표 7 |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| templateId | Long | Y | 경로 변수. 템플릿 ID |

##### Body

없음

##### Example

```
DELETE /api/v1/trash/templates/18
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "TRASH200_3",
  "message": "템플릿을 영구 삭제했습니다.",
  "result": {
    "templateId": 18
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "TRASH404_1",
  "message": "휴지통에서 템플릿을 찾을 수 없어요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| templateId | Long | 삭제된 템플릿 ID |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 404 | TRASH404_1 | 휴지통에서 템플릿을 찾을 수 없어요. | 휴지통에 없는 템플릿(이미 복구·영구 삭제됨, 다른 사람 템플릿) |

##### 비고

- 휴지통에 있는 템플릿만 삭제됩니다. 내 템플릿(휴지통 밖)을 바로 영구 삭제하는 경로는 없습니다.
- 복구 불가.

---

### 18. DELETE /api/v1/trash/templates

| 항목 | 내용 |
| --- | --- |
| 설명 | 휴지통 비우기 (전체 영구 삭제) |
| 관련 기능 | BIN-02, BIN-03, 정책표 7 |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

없음

##### Body

없음

##### Example

```
DELETE /api/v1/trash/templates
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "TRASH200_4",
  "message": "휴지통을 비웠습니다.",
  "result": {
    "deletedCount": 4
  }
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| deletedCount | Integer | 영구 삭제된 개수 (비어 있었으면 0, 에러 아님) |

##### Error Code

이 API 전용 에러 없음 (공통 에러만)

##### 비고

- 성공 시 화면은 4번 빈 화면으로 전환.
- 복구 불가.

---


## 갤러리

### 19. GET /api/v1/gallery/templates

| 항목 | 내용 |
| --- | --- |
| 설명 | 갤러리 템플릿 전체 조회 |
| 관련 기능 | GAL-01, GAL-02, GAL-03, GAL-04, DSH-09(상단 통합 검색) |
| 로그인 | 불필요 |
| 캐시 | public, max-age=300 |

#### Request

##### Header

```
Content-Type: application/json
```

##### Parameter

없음

##### Body

없음

##### Example

```
GET /api/v1/gallery/templates
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "GALLERY200_2",
  "message": "갤러리 조회에 성공했습니다.",
  "result": {
    "totalCount": 24,
    "templates": [
      {
        "galleryTemplateId": 3,
        "category": "WORK",
        "categoryName": "업무",
        "title": "프리랜서 견적 안내",
        "shortcut": "/견적",
        "content": "안녕하세요 {{고객명}}님,\n요청하신 작업의 견적을 안내드립니다.\n- 작업 범위: {{작업범위}}\n- 견적 금액: {{금액}}원\n- 작업 기간: {{기간}}",
        "variables": [
          "고객명",
          "작업범위",
          "금액",
          "기간"
        ]
      }
    ]
  }
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| totalCount | Integer | 전체 개수 |
| templates[].galleryTemplateId | Long | 갤러리 템플릿 ID |
| templates[].category | String | WORK / SALES / CUSTOMER_SERVICE / PERSONAL |
| templates[].categoryName | String | 카테고리 칩 이름 (업무 / 영업 / 고객 응대 / 개인) |
| templates[].title | String | 제목 |
| templates[].shortcut | String | 기본 단축어 |
| templates[].content | String | 본문 전체 (카드에서 3줄 말줄임) |
| templates[].variables | String[] | 변수 이름 (미리보기 패널 "변수 N개") |

##### Error Code

이 API 전용 에러 없음 (공통 에러만)

##### 비고

- **100개 이하라 페이지 나눔 없이 전체를 내려줍니다.** 카테고리 필터(GAL-01), 탭 옆 개수는 화면에서 계산합니다.
- **상단 통합 검색(DSH-09)도 이 응답으로 처리합니다.** 내 템플릿 화면에서 검색을 시작했는데 아직 이 목록을 받지 않았다면 그때 한 번 부릅니다(공개 캐시 5분이라 부담 없음). 규칙은 공통 규칙 11장.
- 로그인 없이 호출하며 **누구에게나 같은 응답**입니다(공개 캐시 5분). 사람마다 다른 값은 넣지 않습니다.
- 공개 상태(is_published)인 템플릿만, 노출 순서(display_order)대로 내려줍니다.

---

### 20. GET /api/v1/gallery/templates/{galleryTemplateId}

| 항목 | 내용 |
| --- | --- |
| 설명 | 갤러리 템플릿 상세 조회 (미리보기 패널) |
| 관련 기능 | GAL-06 |
| 로그인 | 불필요 |
| 캐시 | public, max-age=300 |

#### Request

##### Header

```
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| galleryTemplateId | Long | Y | 경로 변수. 갤러리 템플릿 ID |

##### Body

없음

##### Example

```
GET /api/v1/gallery/templates/3
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "GALLERY200_3",
  "message": "갤러리 템플릿 조회에 성공했습니다.",
  "result": {
    "galleryTemplateId": 3,
    "category": "WORK",
    "categoryName": "업무",
    "title": "프리랜서 견적 안내",
    "shortcut": "/견적",
    "content": "안녕하세요 {{고객명}}님,\n요청하신 작업의 견적을 안내드립니다.\n- 작업 범위: {{작업범위}}\n- 견적 금액: {{금액}}원\n- 작업 기간: {{기간}}",
    "variables": [
      "고객명",
      "작업범위",
      "금액",
      "기간"
    ]
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "GALLERY404_1",
  "message": "갤러리 템플릿을 찾을 수 없어요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| galleryTemplateId | Long | 갤러리 템플릿 ID |
| category | String | 카테고리 코드 |
| categoryName | String | 카테고리 이름 |
| title | String | 제목 |
| shortcut | String | 기본 단축어 |
| content | String | 본문 전체 |
| variables | String[] | 변수 이름 |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 404 | GALLERY404_1 | 갤러리 템플릿을 찾을 수 없어요. | 없거나 비공개 처리된 갤러리 템플릿 |

##### 비고

- 패널은 목록 데이터로 바로 그려도 됩니다. S3 랜딩의 갤러리 상세 페이지(검색 노출용)에서 재사용하려고 분리했습니다.

---

### 21. POST /api/v1/gallery/templates/{galleryTemplateId}/save

| 항목 | 내용 |
| --- | --- |
| 설명 | 갤러리 템플릿을 내 템플릿으로 복사 저장 |
| 관련 기능 | GAL-05 (기획 확정: 같은 템플릿 여러 번 저장 가능, 저장됨 표시 없음) |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

| 파라미터 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| galleryTemplateId | Long | Y | 경로 변수. 갤러리 템플릿 ID |

##### Body

없음

##### Example

```
POST /api/v1/gallery/templates/3/save
```

#### Response

##### 성공 예시 (201, 단축어 그대로)

```json
{
  "isSuccess": true,
  "code": "GALLERY201_1",
  "message": "내 템플릿에 저장되었습니다.",
  "result": {
    "templateId": 21,
    "title": "프리랜서 견적 안내",
    "shortcut": "/견적",
    "originalShortcut": "/견적",
    "shortcutChanged": false
  }
}
```

##### 성공 예시 (201, 단축어 충돌 → 자동 번호)

```json
{
  "isSuccess": true,
  "code": "GALLERY201_1",
  "message": "내 템플릿에 저장되었습니다.",
  "result": {
    "templateId": 22,
    "title": "프리랜서 견적 안내",
    "shortcut": "/견적2",
    "originalShortcut": "/견적",
    "shortcutChanged": true
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "TEMPLATE403_1",
  "message": "템플릿 저장 한도에 도달했어요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| templateId | Long | 새로 만들어진 내 템플릿 ID |
| title | String | 토스트용 이름 |
| shortcut | String | 최종 단축어 |
| originalShortcut | String | 갤러리 원본 단축어 |
| shortcutChanged | Boolean | 자동 번호가 붙었는지 |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 403 | TEMPLATE403_1 | 템플릿 저장 한도에 도달했어요. | 활성 템플릿 수가 플랜 한도(Free 5 / Pro 50) 이상. 화면은 한도 모달 노출 |
| 409 | TEMPLATE409_2 | 사용할 수 있는 단축어 번호가 없어요. | 자동 번호 2~99가 모두 사용 중 |
| 404 | GALLERY404_1 | 갤러리 템플릿을 찾을 수 없어요. | 없거나 비공개 처리된 갤러리 템플릿 |

##### 비고

- 제목·단축어·본문을 복사해 새 템플릿을 만들고 출처(갤러리 ID)를 기록합니다. 이후 갤러리 원본이 바뀌어도 내 템플릿은 그대로입니다.
- 한도 도달 시 저장하지 않고 TEMPLATE403_1 → 4-2 한도 모달.
- 자동 번호 규칙은 복구 API와 같습니다.
- 저장 후에도 갤러리 화면에 머무릅니다. 성공 후 사용량 API로 사이드바 갱신.
- 비로그인 사용자는 화면에서 로그인으로 유도만 합니다(가입 후 자동 저장 없음).
- **같은 갤러리 템플릿을 여러 번 저장할 수 있습니다(기획 확정).** 저장할 때마다 내 템플릿이 새로 하나씩 생기고, 각각 플랜 한도를 한 칸씩 차지합니다.
- 단축어가 이미 있으면 자동 번호를 붙여 저장합니다(`/견적` → `/견적2` → `/견적3`). 2~99가 모두 쓰이면 TEMPLATE409_2.
- 갤러리에서 가져온 템플릿은 내용만 복사하고 원본과 동기화하지 않습니다. `source_gallery_template_id`는 출처 기록·집계용입니다(원본이 삭제되면 비워짐).
- 화면에 "저장됨" 표시는 없습니다. 저장 버튼은 항상 그대로 동작합니다.

---


## AI 톤 교정

### 22. POST /api/v1/ai/tone-corrections

| 항목 | 내용 |
| --- | --- |
| 설명 | AI 톤 교정 실행 (Pro 전용, 하루 10회) |
| 관련 기능 | AIT-01, AIT-02, AIT-04, DSH-03, 정책표 8 |
| 로그인 | 필요 |
| 캐시 | no-store |

#### Request

##### Header

```
Authorization: Bearer accessToken
Content-Type: application/json
```

##### Parameter

없음

##### Body

| 필드 | 데이터 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| style | String | Y | 문체 코드 1개. BOSS_EXTERNAL(상사/외부용) / REPORT_OFFICIAL(보고/공문용) / MARKETING(마케팅/설득) / NOTICE(공지/안내) / SOFT_POLITE(부드러운 존댓말) / CASUAL(편한 반말) / ESSAY(감성/에세이) |
| text | String | Y | 교정할 원문. 1~3000자 |

##### Example

```
POST /api/v1/ai/tone-corrections

{
  "style": "BOSS_EXTERNAL",
  "text": "이번 달은 일정이 꽉 차서 외주 못 받는다고 해줘"
}
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "AI200_1",
  "message": "톤 교정에 성공했습니다.",
  "result": {
    "correctedText": "안녕하세요. 연락 주셔서 감사합니다.\n아쉽게도 이번 달은 일정이 모두 차 있어 새로운 외주를 맡기 어려운 상황입니다.\n양해 부탁드리며, 다음에 꼭 함께할 수 있기를 바랍니다.",
    "style": "BOSS_EXTERNAL",
    "keyInfoWarning": null,
    "usage": {
      "usedCount": 4,
      "limit": 10,
      "remaining": 6,
      "resetAt": "2026-10-12T00:00:00+09:00"
    }
  }
}
```

##### 성공 예시 (원문의 날짜·금액이 결과에서 빠지거나 바뀐 경우)

```json
{
  "isSuccess": true,
  "code": "AI200_1",
  "message": "톤 교정에 성공했습니다.",
  "result": {
    "correctedText": "안녕하세요. 말씀하신 조건으로 진행 가능함을 알려드립니다.",
    "style": "REPORT_OFFICIAL",
    "keyInfoWarning": {
      "message": "원문의 일부 정보가 빠지거나 변경되었을 수 있어요. 확인 후 사용해주세요.",
      "missingValues": [
        "10월 14일",
        "300만 원"
      ],
      "addedValues": []
    },
    "usage": {
      "usedCount": 5,
      "limit": 10,
      "remaining": 5,
      "resetAt": "2026-10-12T00:00:00+09:00"
    }
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "AI429_1",
  "message": "오늘 톤 교정 횟수를 모두 사용했어요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| correctedText | String | 교정된 문장 |
| style | String | 적용된 문체 |
| keyInfoWarning | Object? | 핵심 정보 경고. 문제 없으면 null |
| keyInfoWarning.message | String | 화면에 그대로 띄울 통합 문구 |
| keyInfoWarning.missingValues | String[] | 원문에 있었는데 결과에서 빠진 값 (참고용) |
| keyInfoWarning.addedValues | String[] | 결과에 새로 생긴 값 (참고용) |
| usage.usedCount | Integer | 차감 후 오늘 사용 횟수 → 버튼 라벨 "톤 교정하기(4/10)" |
| usage.limit | Integer | 일일 한도 |
| usage.remaining | Integer | 남은 횟수 (0이면 버튼 비활성) |
| usage.resetAt | String | 리셋 시각 → 카운트다운 |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 400 | AI400_1 | 문체를 선택해주세요. | style 누락 또는 값 오류 |
| 400 | AI400_2 | 교정할 문장을 입력해주세요. | text가 비었거나 공백만 입력 |
| 400 | AI400_3 | 문장은 3,000자까지 입력할 수 있어요. | text 3000자 초과 |
| 403 | AI403_1 | AI 톤 교정은 Pro 플랜에서 사용할 수 있어요. | Free 사용자 |
| 422 | AI422_1 | 혐오 표현이 포함되어 있어 답변을 생성할 수 없습니다. | 혐오·차별 표현 감지. 횟수 미차감 |
| 429 | AI429_1 | 오늘 톤 교정 횟수를 모두 사용했어요. | 일일 10회 소진. 자정(KST) 리셋 |
| 502 | AI502_1 | 일시적인 오류로 요청을 처리하지 못했어요. 다시 한 번 물어봐 주시겠어요? | 외부 AI 오류. 횟수 미차감 |
| 504 | AI504_1 | 일시적인 오류로 요청을 처리하지 못했어요. 다시 한 번 물어봐 주시겠어요? | 외부 AI 20초 타임아웃. 횟수 미차감 |

##### 비고

- **핵심 정보 검사**: 원문과 결과에서 날짜·시간·금액·단위가 붙은 숫자를 뽑아 같은 형태로 바꾼 뒤 비교합니다. 빠지거나 새로 생긴 값이 있으면 `keyInfoWarning`을 채웁니다. 고유명사는 검사하지 않습니다.
- 경고가 있어도 **결과는 정상 응답**이고 횟수도 차감됩니다. 화면은 결과 아래에 `keyInfoWarning.message`를 표시합니다(기획 확정 통합 문구).
- **횟수 처리**: 요청 시 1회를 먼저 잡아두고(남은 횟수 없으면 AI429_1), AI 오류·타임아웃·혐오 표현 차단이면 되돌립니다.
- **처리 중 이탈**: 서버가 연결 끊김을 감지하면 되돌리지만, 이미 결과가 만들어진 뒤 이탈하면 차감될 수 있습니다.
- 외부 AI 대기 시간은 20초. 넘으면 AI504_1. 출력 토큰 상한(4,000)을 반드시 설정합니다(비용 상한).
- 원문과 결과는 저장하지도, 로그에 남기지도 않습니다 (정책표 8). 에러 로그에도 원문 제외.
- "이전 버전으로 돌아가기"(AIT-04)용 이력은 화면에서만 보관합니다.
- 플랜 판정은 결제 도입 전 방식(사용량 API 비고 참고)을 따릅니다. Free면 AI403_1.

---
