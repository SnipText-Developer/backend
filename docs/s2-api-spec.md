# SNIPTEXT API 명세 (S2 크롬 확장) v0.2

| 항목 | 내용 |
| --- | --- |
| 버전 | v0.2 (2026-11-22, UT 전 확정) |
| v0.1 대비 | 웹 주소를 실제 주소(`sniptext.kr`)로 수정. 자동 연결(흐름 A) 조건 추가: 확장에서 직접 로그아웃한 뒤에는 자동 연결하지 않음(기능명세 COM-06·EXT-02). 확장의 모든 API 호출은 백그라운드 서비스 워커에서. 확인 필요 표 정리(로그아웃 범위 확정, 네이버 버튼 숨김 추가). API·응답 형식 변경 없음 |
| 기준 문서 | 크롬 확장 프로그램 기능명세서 RD 1.0.1, 피그마 "확장 v1.0.1", S1 API 명세 v0.8, ERD v0.8 |
| 기본 경로 | `/api/v1` |
| 새 API | 5개 (23~27번). S1 번호에 이어서 붙임 |
| 다시 쓰는 S1 API | 4·5·6·14·22번 (요청·응답 변경 없음) |
| DB 변경 | `extension_link_codes` 테이블 추가 (아래 "ERD 추가" 참고) |

## 목록

| # | 도메인 | 메서드 | URI | 설명 | 로그인 | 캐시 |
| --- | --- | --- | --- | --- | --- | --- |
| 23 | 인증 | POST | `/api/v1/auth/extension/link-codes` | 확장 연결 코드 발급 (웹에 로그인된 상태에서 확장 자동 로그인) | 필요 | 금지 |
| 24 | 인증 | POST | `/api/v1/auth/extension/token` | 연결 코드로 확장 토큰 발급 | 불필요 | 금지 |
| 25 | 인증 | POST | `/api/v1/auth/extension/reissue` | 확장 접근 토큰 재발급 (재발급 토큰 교체) | 불필요 | 금지 |
| 26 | 인증 | POST | `/api/v1/auth/extension/logout` | 확장 로그아웃 | 불필요 | 금지 |
| 27 | 내 템플릿 | GET | `/api/v1/templates/sync` | 확장 템플릿 동기화 (바뀐 게 있을 때만 전체 목록) | 필요 | 금지 |

## 기능별로 쓰는 API

| 기능 (기능명세서 ID) | 쓰는 API | 비고 |
| --- | --- | --- |
| COM-06 웹 로그인 상태 자동 연결 | 23 → 24 | 공통 규칙 1장 흐름 A |
| EXT-01-1 로그인 버튼 | 23 → 24 | 웹 로그인 화면을 새 탭으로 엶 (흐름 B). EXT-01-2 네이버 버튼은 네이버 로그인 추가 전까지 숨김 (확인 필요 2번) |
| EXT-01-3 약관 링크 | 없음 | 랜딩 약관 페이지 새 탭 |
| EXT-02 프로필 | 5번 | 24번 응답에도 들어 있어 연결 직후엔 바로 그림 |
| EXT-02 로그아웃 | 26번 | |
| EXT-02 대시보드 열기, EXT-04 새 템플릿 | 없음 | 대시보드 새 탭 |
| EXT-03 검색, EXT-03-1 결과 없음 | 없음 | 로컬 목록으로 화면에서 검색 |
| EXT-065 템플릿 리스트 | 27번 | 로컬 목록, 정렬은 오래된 순 (S1과 같음) |
| EXT-065 복사, EXT-06·07 붙여넣기 | 14번 `POPUP_COPY` | 클립보드 복사 성공 후 |
| EXT-06 변수 입력, EXT-07 미리보기 | 없음 | 로컬 목록의 `content`·`variables` |
| MINI-00~03 슬래시 팝업·변수 입력 | 14번 `EMAIL_INLINE` | 목록·검색·최근 사용은 로컬, 삽입 성공 후 사용 기록 |
| MINI-04 `/톤교정`, 팝업 "AI 톤교정" 화면 | 22번, 6번 | 버튼 라벨 "톤 교정하기(0/5)"는 6번 |
| 웹에서 로그아웃 (DSH-07) | 4번 | 4번이 확장 재발급 토큰까지 폐기. 같은 브라우저는 웹이 확장에 메시지를 보내 즉시 정리 |

## 공통 규칙 (S1 공통 규칙에 더하는 것)

S1 공통 규칙 1~11장은 그대로 적용합니다. 아래는 확장에만 해당하는 내용입니다.

### 1. 확장 인증

**로그인은 웹에서만 하고, 웹이 일회용 연결 코드를 확장에 넘깁니다.**

- 웹의 재발급 토큰은 HttpOnly 쿠키라 확장에 넘길 수 없습니다 (S1 공통 1장).
- 확장에서 구글 로그인을 따로 만들면 약관 동의 화면(AUTH-04)까지 확장에 다시 만들어야 하고, 확장용 구글 앱 설정도 따로 필요합니다.
- 연결 코드 방식이면 확장은 로그인 화면 없이 "웹 로그인 화면 열기" 버튼만 있으면 됩니다.

**흐름 A. 웹에 이미 로그인된 상태 (COM-06)**

```
1. 웹(대시보드)이 열릴 때와 탭으로 돌아올 때 확장에 상태를 물음
   chrome.runtime.sendMessage(확장 ID, { type: "SNIPTEXT_STATUS" })
   → 확장 미설치면 응답 없음 (끝)
   → { loggedIn: true } 이면 끝
   → { loggedIn: false, autoLink: false } 이면 끝 (확장에서 직접 로그아웃한 상태)
2. 확장이 { loggedIn: false, autoLink: true } 로 답하면 웹이 23번으로 연결 코드 발급
3. 웹이 확장에 연결 코드 전달
   chrome.runtime.sendMessage(확장 ID, { type: "SNIPTEXT_LINK", linkCode })
4. 확장(백그라운드)이 24번으로 교환 → 접근·재발급 토큰 저장 → 27번으로 목록 받기
```

**자동 연결 조건 (`autoLink`)**

기능명세 COM-06은 "설치 직후" 자동 로그인, EXT-02는 확장 로그아웃 시 확장 로그인 화면(EXT-01)으로 이동입니다. 확장에서 직접 로그아웃했는데 웹이 다시 자동 연결하면 로그아웃이 풀리므로 아래처럼 나눕니다.

| 확장 상태 | `autoLink` | 다시 로그인하는 방법 |
| --- | --- | --- |
| 설치 직후 (한 번도 로그인 안 함) | true | 웹을 열면 자동 연결 (흐름 A) |
| 재발급 실패 (`AUTH401_5`, 14일 만료 등) | true | 웹을 열면 자동 연결 |
| 웹에서 로그아웃 (S1 4번, 같은 브라우저 메시지) | true | 웹에서 다시 로그인하면 자동 연결 |
| **확장에서 직접 로그아웃 (26번)** | **false** | 확장의 로그인 버튼으로만 (흐름 B) |

- `autoLink` 값은 확장이 `chrome.storage.local`에 저장합니다. 흐름 B로 다시 로그인하면 true로 돌립니다.
- 서버는 이 값을 모릅니다 (서버 변경 없음).

**흐름 B. 확장의 로그인 버튼 (EXT-01-1)**

```
1. 확장이 웹 로그인 화면을 새 탭으로 엶
   https://sniptext.kr/?login=extension  (로그인 화면 주소는 프론트에서 확정)
2. 웹에서 로그인 (처음 온 사용자는 약관 동의까지)
3. 로그인 성공 직후 흐름 A의 2~4 (?login=extension 으로 들어왔으면 autoLink 값과 상관없이 연결)
4. 연결이 끝나면 웹은 "확장 프로그램에 로그인되었어요" 안내 후 원래 화면으로
```

**흐름 C. 토큰 유지**

| 구분 | 내용 |
| --- | --- |
| 저장 위치 | `chrome.storage.local` (접근 토큰 30분, 재발급 토큰 14일) |
| 재발급 | 25번. **백그라운드 서비스 워커 한 곳에서만, 동시에 하나만** 호출. 팝업·콘텐츠 스크립트는 백그라운드에서 토큰을 받아 씀 |
| 재발급 실패 | `AUTH401_5` → 로컬 토큰·목록 삭제 후 로그아웃 화면(EXT-01) |
| 요청 | 쿠키를 보내지 않음 (`credentials: "omit"`). 접근 토큰은 S1과 같이 `Authorization` 헤더 |
| **API 호출 위치** | **모든 API 호출은 백그라운드 서비스 워커에서만.** 팝업·콘텐츠 스크립트는 `chrome.runtime.sendMessage`로 백그라운드에 요청하고 결과만 받음 |

- 재발급 토큰은 쓸 때마다 바뀝니다. 팝업과 백그라운드가 같은 재발급 토큰으로 동시에 25번을 부르면 두 번째 요청이 "이미 쓴 토큰 재사용"으로 판정되어 계보 전체가 폐기되고 로그아웃됩니다. 그래서 재발급은 한 곳에서만 합니다.
- 메일 작성창에서 도는 콘텐츠 스크립트가 API를 직접 부르면 메일 사이트(예: `mail.google.com`) 출처로 요청되어 CORS에 막힙니다. 14번 `EMAIL_INLINE`, 22번 `/톤교정`도 백그라운드를 거칩니다.

**서버·확장 설정**

| 항목 | 설정 |
| --- | --- |
| 확장 manifest `host_permissions` | `https://api.sniptext.kr/*` (백그라운드에서 API 호출) |
| 외부 출처 허용 (CORS) | `chrome-extension://{확장 ID}` 추가. 쿠키를 쓰지 않으므로 이 출처는 자격 증명 허용 불필요 |
| 확장 manifest `externally_connectable` | `https://sniptext.kr/*` (개발 중에는 `http://localhost:3000/*` 추가) |
| 확장 ID 고정 | 웹이 확장 ID로 메시지를 보내므로 고정해야 함. 개발 중에는 manifest `key`로 고정, 웹스토어 첫 업로드 후 그 ID로 확정 |
| 메시지 검증 | 확장은 `sender.origin`이 위 허용 주소일 때만 `SNIPTEXT_LINK`를 받음 |

### 2. 로컬 목록과 동기화

PRD F3 합의대로 "같은 브라우저는 즉시, 다른 기기는 최대 1분 이내"를 목표로 합니다.

| 시점 | 동작 |
| --- | --- |
| 확장 팝업을 열 때 | 27번 (저장된 목록으로 먼저 그리고, 응답이 오면 바뀐 경우만 다시 그림 → 팝업 300ms 목표) |
| 메일 작성창에 포커스가 갈 때 | 27번. 마지막 동기화 후 1분이 지났을 때만 |
| 1분마다 | 27번 (`chrome.alarms`, 최소 간격 30초) |
| 웹에서 생성·수정·삭제·복구·갤러리 저장 성공 | 웹이 확장에 `{ type: "SNIPTEXT_TEMPLATES_CHANGED" }` 메시지 → 27번 |
| 확장에서 복사·삽입 성공 | 14번 응답의 `lastUsedAt`으로 로컬 목록만 바로 고침 (27번 호출 안 함) |
| 로그아웃, 재발급 실패 | 로컬 토큰·목록 삭제 |

- **검색 API는 만들지 않습니다.** 확장 검색(EXT-03, MINI-02)·최근 사용(MINI-01)·변수 목록은 모두 로컬 목록으로 처리합니다. 이유는 S1 공통 11장과 같고, 메일 삽입 반응 100ms 목표도 로컬 목록이어야 지킬 수 있습니다.
- **확장 검색 규칙**: S1 공통 11장 "검색 규칙"과 같습니다. 범위만 내 템플릿으로 한정합니다.
- **MINI-02 매칭**: 단축어 앞부분 일치를 먼저, 그다음 포함 일치 순으로 최대 3개 (기능명세 "가장 근접한 일치"를 이렇게 구현). 비교는 S1 공통 5장과 같이 NFC 정규화 + 소문자.
- **최근 사용 (MINI-01)**: `lastUsedAt`이 있는 템플릿을 최근 순으로 상위 N개. N은 확인 필요 (아래 "확인 필요" 1번).
- **변수 입력 (EXT-06, MINI-03)**: 서버가 내려준 `variables` 순서대로 필드를 만듭니다. 화면에서 변수를 다시 뽑지 않습니다 (S1과 같은 규칙 보장).

### 3. 결과 코드 추가

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 401 | AUTH401_6 | 연결 시간이 지났어요. 다시 로그인해주세요. | 연결 코드가 없음·만료(1분)·이미 사용됨 (24번) |

- 성공 코드: `AUTH201_2`(23번), `AUTH200_4`(24번), `AUTH200_5`(25번), `AUTH200_6`(26번), `TEMPLATE200_7`(27번).
- S1에서 삭제된 번호(`AUTH400_2`, `AUTH401_1`, `AUTH401_2`)는 다시 쓰지 않습니다.

### 4. 확인 필요 (기획·디자인)

모두 화면 문구·디자인 요청이며 서버 API에는 영향이 없습니다.

| # | 내용 | 제안 |
| --- | --- | --- |
| 1 | 최근 사용 개수: 기능명세 MINI-01은 3개, ERD·S1 명세는 "최근 사용 5개" | 기능명세대로 3개 (화면 상수) |
| 2 | 네이버 로그인 버튼(EXT-01-2): 서버는 구글만 지원 | 네이버 로그인 추가 전까지 확장 로그인 화면에서 네이버 버튼 숨김 |
| 3 | EXT-01-3 "회원가입 시 이용약관에 동의하게 됩니다" 문구 | 가입은 웹 약관 동의 화면에서만 하므로 "로그인하면 웹에서 SnipText를 시작해요" 같은 문구로 변경 |
| 4 | 피그마 문체 선택에 "감성/에세이"가 남아 있음 (팝업 AI 톤교정 화면, MINI-04 목록 7종) | 11/10 삭제 확정대로 6종. 서버는 6종만 받음 (`AI400_1`) |
| 5 | MINI-04·EXT-04에 "Pro 플랜", "Free 한도(5/5)" 문구가 남아 있음 | 결제 제거(10/31) 반영: 모든 사용자 AI 하루 5회, 템플릿 50개 |
| 6 | 템플릿 0개 빈 화면: 피그마 6-1 "아직 저장된 템플릿이 없어요"가 기능명세 EXT-065에 없음 | 기능명세에 6-1 반영 |
| 7 | 확장의 템플릿 목록이 암호화 없이 브라우저에 남음 | 로그아웃·재발급 실패 시 즉시 삭제. 개인정보처리방침에 "확장 프로그램 로컬 저장" 명시 |

**확정된 것 (v0.1 확인 필요 6번)**: 확장 로그아웃은 확장만 끊고(26번, EXT-02), 웹 로그아웃은 확장까지 끊습니다(S1 4번, DSH-07). 확장에서 직접 로그아웃한 뒤에는 웹이 자동 연결하지 않습니다(공통 규칙 1장 `autoLink`).

## ERD 추가

### `extension_link_codes` — 확장 연결 코드

> 1분짜리 일회용 코드. Redis를 쓰면 이 테이블은 Redis로 대체 가능

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK | ID |
| `user_id` | BIGINT | N |  | FK | 회원 ID |
| `code_hash` | CHAR(64) | N |  | UK | 연결 코드 원문 대신 SHA-256 해시 |
| `expires_at` | DATETIME(6) | N |  |  | 만료 시각 (발급 + 1분) |
| `used_at` | DATETIME(6) | Y |  |  | 사용 시각. 값이 있으면 다시 못 씀 |
| `created_at` | DATETIME(6) | N |  |  | 발급 시각 |

- 유니크 `uk_link_code_hash` (code_hash)
- 인덱스 `idx_link_code_user` (user_id)
- 외래키 `user_id` → `users.id` (삭제 시 CASCADE)
- 배치: 매일 04:00 재발급 토큰 정리 때 만료된 연결 코드도 함께 삭제 (UT 뒤에 추가해도 됨. 코드는 1분 뒤 쓸 수 없으므로 남아 있어도 동작에 문제 없음)

```sql
-- V{yyyyMMddHHmm}__create_extension_link_codes.sql
CREATE TABLE extension_link_codes (
    id                           BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id                      BIGINT NOT NULL COMMENT '회원 ID',
    code_hash                    CHAR(64) NOT NULL COMMENT '연결 코드 원문 대신 SHA-256 해시',
    expires_at                   DATETIME(6) NOT NULL COMMENT '만료 시각 (발급 + 1분)',
    used_at                      DATETIME(6) NULL COMMENT '사용 시각. 값이 있으면 다시 못 씀',
    created_at                   DATETIME(6) NOT NULL COMMENT '발급 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_link_code_hash (code_hash),
    KEY idx_link_code_user (user_id),
    CONSTRAINT fk_link_code_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '확장 연결 코드';
```

- 확장의 재발급 토큰은 기존 `refresh_tokens`에 `client_type = 'EXTENSION'`으로 저장합니다. 테이블 변경 없음.


## 인증

### 23. POST /api/v1/auth/extension/link-codes

| 항목 | 내용 |
| --- | --- |
| 설명 | 확장 연결 코드 발급 (웹에 로그인된 상태에서 확장 자동 로그인) |
| 관련 기능 | COM-06, EXT-01-1, EXT-01-2 |
| 로그인 | 필요 (웹의 접근 토큰) |
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
POST /api/v1/auth/extension/link-codes
```

#### Response

##### 성공 예시 (201)

```json
{
  "isSuccess": true,
  "code": "AUTH201_2",
  "message": "확장 연결 코드가 발급되었습니다.",
  "result": {
    "linkCode": "lc_8Qm2xV7rTn0bY4kJ9wLs3eHd",
    "expiresAt": "2026-11-15T20:31:00+09:00"
  }
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| linkCode | String | 일회용 연결 코드. 확장에 메시지로 넘김 |
| expiresAt | String | 만료 시각 (발급 + 1분) |

##### Error Code

이 API 전용 에러 없음 (공통 에러만)

##### 비고

- 웹(대시보드)만 부릅니다. 확장은 부르지 않습니다.
- 코드는 추측할 수 없는 임의값(32바이트 이상)으로 만들고, DB에는 해시만 저장합니다.
- **1분 안에 한 번만** 쓸 수 있습니다. 웹은 받은 즉시 확장에 넘깁니다.
- 코드를 화면에 보여주거나 주소에 넣지 않습니다 (메시지로만 전달).
- 같은 회원이 여러 번 발급해도 이전 코드를 지우지 않습니다 (각자 1분 뒤 만료).

---

### 24. POST /api/v1/auth/extension/token

| 항목 | 내용 |
| --- | --- |
| 설명 | 연결 코드로 확장 토큰 발급 |
| 관련 기능 | COM-06, EXT-01, EXT-02(프로필 첫 표시) |
| 로그인 | 불필요 (연결 코드 사용) |
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
| linkCode | String | Y | 23번에서 받은 연결 코드 |

##### Example

```
POST /api/v1/auth/extension/token

{
  "linkCode": "lc_8Qm2xV7rTn0bY4kJ9wLs3eHd"
}
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "AUTH200_4",
  "message": "확장 프로그램에 로그인되었습니다.",
  "result": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresAt": "2026-11-15T21:00:30+09:00",
    "refreshToken": "rt_Jx92kLm0Qa7Vb3Nc8Pz1Ws4Yd",
    "refreshTokenExpiresAt": "2026-11-29T20:30:30+09:00",
    "user": {
      "userId": 1,
      "nickname": "스닙이",
      "email": "sniptext@naver.com",
      "profileImageUrl": "https://lh3.googleusercontent.com/a/sample",
      "loginType": "GOOGLE"
    }
  }
}
```

##### 에러 예시

```json
{
  "isSuccess": false,
  "code": "AUTH401_6",
  "message": "연결 시간이 지났어요. 다시 로그인해주세요.",
  "result": null
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| accessToken | String | 접근 토큰 (30분). 웹과 같은 토큰 |
| accessTokenExpiresAt | String | 접근 토큰 만료 시각 → 만료 1분 전에 미리 재발급할 때 사용 |
| refreshToken | String | 확장 재발급 토큰 (14일). **응답 본문으로 줌** (쿠키 아님) |
| refreshTokenExpiresAt | String | 재발급 토큰 만료 시각 |
| user | Object | 프로필 영역(EXT-02) 첫 표시용. 필드는 S1 5번과 같음 |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 401 | AUTH401_6 | 연결 시간이 지났어요. 다시 로그인해주세요. | 연결 코드가 없음·만료(1분)·이미 사용됨 |
| 404 | USER404_1 | 회원 정보를 찾을 수 없어요. | 코드 발급 후 탈퇴한 회원 |

##### 비고

- 코드를 쓰는 순간 `used_at`을 채웁니다. 같은 코드로 동시에 두 번 오면 하나만 성공합니다 (조건부 수정으로 처리).
- 재발급 토큰은 `refresh_tokens`에 `client_type = EXTENSION`, 새 계보(`family_id`)로 저장합니다. 웹 세션과 계보가 따로라 서로 영향이 없습니다.
- `linkCode`가 비어 있으면 `COMMON400_1`입니다.
- 확장은 받은 토큰을 `chrome.storage.local`에 저장하고 곧바로 27번을 부릅니다.

---

### 25. POST /api/v1/auth/extension/reissue

| 항목 | 내용 |
| --- | --- |
| 설명 | 확장 접근 토큰 재발급 (재발급 토큰 교체) |
| 관련 기능 | PRD 8.2(재발급 토큰 교체 방식) |
| 로그인 | 불필요 (재발급 토큰 사용) |
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
| refreshToken | String | Y | 확장에 저장된 재발급 토큰 |

##### Example

```
POST /api/v1/auth/extension/reissue

{
  "refreshToken": "rt_Jx92kLm0Qa7Vb3Nc8Pz1Ws4Yd"
}
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "AUTH200_5",
  "message": "토큰이 재발급되었습니다.",
  "result": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "accessTokenExpiresAt": "2026-11-15T21:30:30+09:00",
    "refreshToken": "rt_Pq81sNd2Lk6Xw0Ve4Ry9Bm5Tc",
    "refreshTokenExpiresAt": "2026-11-29T21:00:30+09:00"
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
| accessTokenExpiresAt | String | 접근 토큰 만료 시각 |
| refreshToken | String | **새 재발급 토큰.** 받으면 바로 저장하고 이전 것은 버림 |
| refreshTokenExpiresAt | String | 새 재발급 토큰 만료 시각 (발급 시점 + 14일) |

##### Error Code

| HttpStatus | ResultCode | ErrorMessage | Description |
| --- | --- | --- | --- |
| 401 | AUTH401_5 | 다시 로그인해주세요. | 재발급 토큰 없음·만료·폐기, 이미 쓴 토큰 재사용 감지(계보 전체 폐기), 웹 토큰을 보낸 경우 |

##### 비고

- 규칙은 S1 3번과 같습니다. 다른 점은 토큰을 쿠키가 아니라 본문으로 주고받는 것뿐입니다.
- `client_type = EXTENSION`인 토큰만 받습니다. 웹 토큰이 오면 `AUTH401_5`입니다.
- 재발급 토큰도 새로 바뀌므로 **백그라운드 서비스 워커 한 곳에서 한 번에 하나만** 호출합니다 (공통 규칙 1장 흐름 C).
- 확장은 접근 토큰 만료 1분 전, 또는 `COMMON401_1`을 받았을 때 부릅니다.
- `refreshToken`이 비어 있으면 `COMMON400_1`입니다.

---

### 26. POST /api/v1/auth/extension/logout

| 항목 | 내용 |
| --- | --- |
| 설명 | 확장 로그아웃 |
| 관련 기능 | EXT-02(로그아웃 아이콘) |
| 로그인 | 불필요 (재발급 토큰 사용) |
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
| refreshToken | String | Y | 확장에 저장된 재발급 토큰 |

##### Example

```
POST /api/v1/auth/extension/logout

{
  "refreshToken": "rt_Pq81sNd2Lk6Xw0Ve4Ry9Bm5Tc"
}
```

#### Response

##### 성공 예시

```json
{
  "isSuccess": true,
  "code": "AUTH200_6",
  "message": "로그아웃되었습니다.",
  "result": null
}
```

##### Error Code

이 API 전용 에러 없음 (공통 에러만)

##### 비고

- **이 확장의 계보만 폐기합니다.** 웹 세션과 다른 기기의 확장은 그대로입니다 (EXT-02). 웹에서 로그아웃하면 S1 4번이 확장까지 모두 폐기합니다 (DSH-07).
- 토큰이 없거나 이미 폐기·만료됐어도 성공으로 응답합니다 (S1 4번과 같은 이유).
- 확장은 응답과 상관없이 로컬 토큰·목록을 지우고, `autoLink`를 false로 저장한 뒤 로그인 화면(EXT-01)으로 갑니다. 오프라인이라 요청이 실패해도 로컬은 지웁니다. 이후 웹을 열어도 자동 연결되지 않습니다 (공통 규칙 1장).

---


## 내 템플릿

### 27. GET /api/v1/templates/sync

| 항목 | 내용 |
| --- | --- |
| 설명 | 확장 템플릿 동기화 (바뀐 게 있을 때만 전체 목록) |
| 관련 기능 | EXT-00, EXT-03, EXT-065, MINI-01, MINI-02, PRD F3(동기화) |
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
| version | String | N | 확장이 갖고 있는 목록의 버전 (직전 응답의 `version`). 처음이면 비움 |

##### Body

없음

##### Example

```
GET /api/v1/templates/sync?version=v1.18.1731669061000000
```

#### Response

##### 성공 예시 (바뀐 것 없음)

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_7",
  "message": "템플릿 동기화에 성공했습니다.",
  "result": {
    "changed": false,
    "version": "v1.18.1731669061000000",
    "syncedAt": "2026-11-15T20:31:10+09:00",
    "totalCount": null,
    "templates": null
  }
}
```

##### 성공 예시 (바뀐 것 있음 → 전체 목록)

```json
{
  "isSuccess": true,
  "code": "TEMPLATE200_7",
  "message": "템플릿 동기화에 성공했습니다.",
  "result": {
    "changed": true,
    "version": "v1.19.1731669312000000",
    "syncedAt": "2026-11-15T20:35:12+09:00",
    "totalCount": 2,
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
        "lastUsedAt": "2026-11-15T09:12:00+09:00",
        "createdAt": "2026-09-20T14:03:00+09:00",
        "editedAt": "2026-10-01T11:40:00+09:00"
      },
      {
        "templateId": 15,
        "title": "택배 주소",
        "shortcut": "/주소",
        "content": "서울특별시 마포구 와우산로 94 (우) 04066",
        "variables": [],
        "lastUsedAt": null,
        "createdAt": "2026-10-02T10:00:00+09:00",
        "editedAt": "2026-10-02T10:00:00+09:00"
      }
    ]
  }
}
```

##### Response Field (result)

| 필드 | 데이터 타입 | 설명 |
| --- | --- | --- |
| changed | Boolean | 보낸 `version` 이후 바뀐 게 있는지. 처음 요청(`version` 없음)이면 항상 true |
| version | String | 지금 목록의 버전. 다음 요청에 그대로 넣음. 화면에서 해석하지 않음 |
| syncedAt | String | 서버 기준 동기화 시각 → "1분 지났는지" 판단용 |
| totalCount | Integer? | 템플릿 수. `changed = false`면 null |
| templates | Object[]? | 휴지통 제외 전체 목록. 필드·정렬은 S1 8번과 같음. `changed = false`면 null |

##### Error Code

이 API 전용 에러 없음 (공통 에러만)

##### 비고

- **바뀐 것만 보내지 않고, 바뀌었으면 전체를 보냅니다.** 최대 50개라 전체를 받아 통째로 바꾸는 게 가장 단순하고, 영구 삭제된 템플릿을 따로 알려줄 필요가 없습니다.
- 버전은 회원의 템플릿(휴지통 포함) 중 가장 늦은 `updated_at`과 휴지통 제외 개수로 만듭니다. `idx_templates_user_updated` 인덱스로 한 번에 구합니다.
    - 생성·수정·휴지통 이동·복구·갤러리 저장·사용 기록은 모두 `updated_at`을 바꾸므로 버전이 바뀝니다.
    - 휴지통 영구 삭제는 목록에 영향이 없어 버전이 바뀌지 않아도 됩니다.
- 사용 기록(14번)도 버전을 바꿉니다. 다른 기기의 "최근 사용" 순서가 맞춰지도록 일부러 포함했습니다.
- `version` 형식이 틀리면 에러 없이 `changed = true`로 전체를 보냅니다.
- S1 8번과 응답 필드가 같으므로 서버는 8번의 조회 결과를 그대로 씁니다.
- 1분 주기 + 팝업 열 때 + 메일 작성창 진입 때 부릅니다 (공통 규칙 2장). 대부분 `changed = false`라 응답이 작습니다.

---
