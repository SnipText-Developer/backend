-- 앱 전용 계정과 출시 전 스키마를 만든다. 마스터 계정으로 한 번만 실행한다 (README "앱 전용 DB 계정")
-- 비밀번호는 이 파일에 쓰지 않는다. MySQL이 난수로 만들어 결과로 한 번만 보여 준다
-- → 그 값을 바로 /sniptext/prod/DB_PASSWORD에 넣는다

CREATE DATABASE IF NOT EXISTS sniptext_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- 결과의 generated password 열이 앱 계정 비밀번호다 (다시 볼 수 없음)
CREATE USER 'sniptext_app'@'%' IDENTIFIED BY RANDOM PASSWORD;

-- 데이터 읽기·쓰기 + 플라이웨이에 필요한 권한만. 다른 스키마·계정 관리 권한은 주지 않는다
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON sniptext_dev.* TO 'sniptext_app'@'%';

SHOW GRANTS FOR 'sniptext_app'@'%';
