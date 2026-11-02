-- 구글 sub는 대소문자를 구분하는 ASCII 최대 255자다 (OpenID Connect 문서).
-- 100자로는 모자라고, 기본 정렬 규칙(utf8mb4_0900_ai_ci)은 대소문자를 구분하지 않아
-- 대소문자만 다른 두 계정을 같은 계정으로 본다. ascii_bin으로 바꿔 글자 그대로 비교한다.
ALTER TABLE social_accounts
    MODIFY provider_user_id VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '구글 계정 고유값(ID 토큰의 sub, 대소문자 구분)';
