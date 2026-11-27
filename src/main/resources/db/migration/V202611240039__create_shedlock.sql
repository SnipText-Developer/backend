-- 배치 잠금 테이블 (셰드락, global/config/SchedulerLockConfig). 배치 이름마다 1행
-- 잠금 시각은 셰드락이 DB 시각(UTC_TIMESTAMP)으로 넣고 비교한다. 시간대 변환이 없도록 DATETIME으로 두고 UTC 값 그대로 저장한다
CREATE TABLE shedlock (
    name                         VARCHAR(64) NOT NULL COMMENT '배치 이름 (@SchedulerLock name)',
    lock_until                   DATETIME(3) NOT NULL COMMENT '잠금이 풀리는 시각 (UTC)',
    locked_at                    DATETIME(3) NOT NULL COMMENT '잠근 시각 (UTC)',
    locked_by                    VARCHAR(255) NOT NULL COMMENT '잠근 서버 (호스트 이름)',
    PRIMARY KEY (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '배치 잠금 (셰드락)';
