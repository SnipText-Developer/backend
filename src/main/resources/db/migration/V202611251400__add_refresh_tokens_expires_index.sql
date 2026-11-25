-- 04:00 정리 배치가 만료된 재발급 토큰만 골라 지우도록 만료 시각 인덱스를 추가한다 (#133).
-- 인덱스가 없으면 삭제할 때 표 전체를 훑으며 행을 잠가, 같은 시각의 재발급·로그아웃이 기다릴 수 있다.
ALTER TABLE refresh_tokens
    ADD INDEX idx_refresh_expires (expires_at);
