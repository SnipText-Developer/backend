-- 플랜 구분 삭제(결제 기능 제거)와 내 템플릿 정렬 고정(생성 순)으로 쓰지 않게 된 컬럼 삭제. ERD v0.8 "마이그레이션" 참고
ALTER TABLE users
    DROP COLUMN plan_override,
    DROP COLUMN template_sort;
