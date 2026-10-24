-- SNIPTEXT S1 ERD - MySQL 8.4 / v0.6 (2026-10-17, 갤러리 여러 번 저장 허용)
-- 시간 컬럼은 모두 KST 기준. 결제·구독 테이블은 PLN 정책 확정 후 추가

-- 회원
CREATE TABLE users (
    id                           BIGINT NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
    email                        VARCHAR(255) NOT NULL COMMENT '구글 계정 이메일',
    nickname                     VARCHAR(50) NOT NULL COMMENT '구글 계정 이름 그대로 (앞뒤 공백 제거, 50자 초과 시 자름, 비어 있으면 이메일 앞부분)',
    profile_image_url            VARCHAR(500) NULL COMMENT '구글 프로필 이미지. 없으면 닉네임 첫 글자 아바타',
    template_sort                VARCHAR(20) NOT NULL DEFAULT 'RECENT_USED' COMMENT '내 템플릿 정렬 기억 (RECENT_USED / RECENT_EDITED)',
    plan_override                VARCHAR(10) NULL COMMENT '결제 도입 전 임시: 값이 있으면 이 플랜(FREE/PRO)으로 판정. 테스트 계정을 Pro로 만들 때 사용',
    last_login_at                DATETIME(6) NULL COMMENT '마지막 로그인 시각',
    created_at                   DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at                   DATETIME(6) NOT NULL COMMENT '수정 시각',
    deleted_at                   DATETIME(6) NULL COMMENT '탈퇴 시각 (탈퇴 화면은 미정)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '회원';

-- 소셜 로그인 연결
CREATE TABLE social_accounts (
    id                           BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id                      BIGINT NOT NULL COMMENT '회원 ID',
    provider                     VARCHAR(20) NOT NULL COMMENT 'GOOGLE',
    provider_user_id             VARCHAR(100) NOT NULL COMMENT '구글 계정 고유값(ID 토큰의 sub)',
    created_at                   DATETIME(6) NOT NULL COMMENT '연결 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_social_provider_user (provider, provider_user_id),
    KEY idx_social_user (user_id),
    CONSTRAINT fk_social_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '소셜 로그인 연결';

-- 약관 동의 기록 : 어떤 회원이 어떤 약관의 어느 버전에 동의했는지. 약관 본문은 랜딩의 버전별 정적 페이지
CREATE TABLE user_terms_agreements (
    id                           BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id                      BIGINT NOT NULL COMMENT '회원 ID',
    terms_type                   VARCHAR(20) NOT NULL COMMENT 'SERVICE(이용약관) / PRIVACY(개인정보 수집·이용). 마케팅 동의가 생기면 MARKETING 추가',
    version                      VARCHAR(20) NOT NULL COMMENT '동의한 약관 버전 (예: v1). 현재 버전은 서버 설정값',
    agreed_at                    DATETIME(6) NOT NULL COMMENT '동의 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_terms_user_type_version (user_id, terms_type, version),
    CONSTRAINT fk_terms_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '약관 동의 기록';

-- 재발급 토큰 : Redis를 쓰면 이 테이블은 Redis로 대체 가능
CREATE TABLE refresh_tokens (
    id                           BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id                      BIGINT NOT NULL COMMENT '회원 ID',
    client_type                  VARCHAR(20) NOT NULL COMMENT 'WEB / EXTENSION',
    token_hash                   CHAR(64) NOT NULL COMMENT '토큰 원문 대신 SHA-256 해시 저장',
    family_id                    CHAR(36) NOT NULL COMMENT '재발급 계보. 이미 쓴 토큰이 다시 오면 계보 전체 폐기',
    expires_at                   DATETIME(6) NOT NULL COMMENT '만료 시각',
    revoked_at                   DATETIME(6) NULL COMMENT '폐기 시각 (로그아웃·교체)',
    created_at                   DATETIME(6) NOT NULL COMMENT '발급 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user (user_id),
    KEY idx_refresh_family (family_id),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '재발급 토큰';

-- 갤러리 템플릿 : 운영자가 DB에 직접 등록. 공개 데이터라 암호화하지 않음
CREATE TABLE gallery_templates (
    id                           BIGINT NOT NULL AUTO_INCREMENT COMMENT '갤러리 템플릿 ID',
    category                     VARCHAR(20) NOT NULL COMMENT 'WORK / SALES / CUSTOMER_SERVICE / PERSONAL',
    title                        VARCHAR(50) NOT NULL COMMENT '제목',
    shortcut                     VARCHAR(11) NOT NULL COMMENT '기본 단축어 (/ 포함)',
    content                      TEXT NOT NULL COMMENT '본문',
    display_order                INT NOT NULL DEFAULT 0 COMMENT '노출 순서',
    is_published                 TINYINT(1) NOT NULL DEFAULT 1 COMMENT '공개 여부',
    slug                         VARCHAR(100) NULL COMMENT 'S3 랜딩 상세 페이지 주소용 이름',
    created_at                   DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at                   DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gallery_slug (slug),
    KEY idx_gallery_category (is_published, category, display_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '갤러리 템플릿';

-- 내 템플릿 (휴지통 포함)
CREATE TABLE templates (
    id                           BIGINT NOT NULL AUTO_INCREMENT COMMENT '템플릿 ID',
    user_id                      BIGINT NOT NULL COMMENT '회원 ID',
    source_gallery_template_id   BIGINT NULL COMMENT '갤러리에서 저장한 경우 원본 ID (출처 기록·집계용, 같은 원본 여러 번 가능)',
    title                        VARCHAR(50) NOT NULL COMMENT '이름. 앞뒤 공백 제거 후 1~50자',
    shortcut                     VARCHAR(11) NOT NULL COMMENT '표시용 단축어. / 포함, 입력한 대소문자 그대로 (/Quote)',
    shortcut_key                 VARCHAR(11) COLLATE utf8mb4_bin NOT NULL COMMENT '비교용 단축어. NFC 정규화 + 소문자 (/quote)',
    active_shortcut_key          VARCHAR(11) COLLATE utf8mb4_bin GENERATED ALWAYS AS (IF(deleted_at IS NULL, shortcut_key, NULL)) STORED COMMENT '생성 컬럼. 휴지통이면 NULL → 단축어 점유 해제',
    content                      TEXT NOT NULL COMMENT '본문 3000자. 서버에서 암호화해 저장',
    last_used_at                 DATETIME(6) NULL COMMENT '최근 사용 시각 → 최근 사용순 정렬, 확장 팝업 최근 사용 5개',
    edited_at                    DATETIME(6) NOT NULL COMMENT '제목·단축어·본문을 고친 시각 → 최근 수정순. 사용 기록으로는 안 바뀜',
    deleted_at                   DATETIME(6) NULL COMMENT '휴지통 이동 시각. 30일 뒤 배치로 영구 삭제',
    created_at                   DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at                   DATETIME(6) NOT NULL COMMENT '모든 변경(사용 기록·삭제 포함) → 확장 동기화 기준',
    PRIMARY KEY (id),
    UNIQUE KEY uk_templates_user_active_shortcut (user_id, active_shortcut_key),
    KEY idx_templates_user_deleted (user_id, deleted_at),
    KEY idx_templates_user_updated (user_id, updated_at),
    KEY idx_templates_gallery (source_gallery_template_id),
    KEY idx_templates_deleted_at (deleted_at),
    CONSTRAINT fk_templates_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_templates_gallery FOREIGN KEY (source_gallery_template_id) REFERENCES gallery_templates (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '내 템플릿 (휴지통 포함)';

-- AI 톤 교정 일일 사용량 : 날짜별 1행이라 자정 리셋 배치가 필요 없음. 원문·결과는 저장하지 않음
CREATE TABLE ai_tone_daily_usages (
    id                           BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id                      BIGINT NOT NULL COMMENT '회원 ID',
    usage_date                   DATE NOT NULL COMMENT 'KST 기준 날짜',
    used_count                   INT NOT NULL DEFAULT 0 COMMENT '그날 사용 횟수',
    updated_at                   DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_usage_user_date (user_id, usage_date),
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI 톤 교정 일일 사용량';
