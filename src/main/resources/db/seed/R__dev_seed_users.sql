-- 개발용 시드 회원 (local·dev 전용). 운영 마이그레이션 위치(db/migration)에는 절대 넣지 않는다.
-- 개발용 임시 토큰(POST /api/v1/auth/dev-token)으로 이 번호의 토큰을 받아 쓴다. 1번 FREE, 2번 PRO
-- 이미 있으면 건너뛴다 (내용을 바꾸면 반복 마이그레이션이 다시 실행되지만 기존 행은 그대로 둔다)
INSERT IGNORE INTO users (id, email, nickname, plan_override, created_at, updated_at)
VALUES (1, 'free@sniptext.local', '무료회원', NULL, '2026-10-26 00:00:00.000000', '2026-10-26 00:00:00.000000'),
       (2, 'pro@sniptext.local', '프로회원', 'PRO', '2026-10-26 00:00:00.000000', '2026-10-26 00:00:00.000000');
