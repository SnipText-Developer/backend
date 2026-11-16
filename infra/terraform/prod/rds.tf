# 운영 DB: RDS MySQL 8.4, 단일 AZ. 공개 접근을 끄고 서버 보안 그룹에서 오는 3306만 연다
# 공개 서브넷에 두지만 공인 주소가 없어 바깥에서는 닿지 않는다 (설계 문서 4-5, 4-8)

resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.public[*].id

  tags = { Name = local.name }
}

resource "aws_security_group" "db" {
  name        = "${local.name}-db"
  description = "SnipText RDS: MySQL from app server only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-db" }
}

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.db.id
  description                  = "MySQL from app server"
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
  referenced_security_group_id = aws_security_group.app.id
}

# 나가는 규칙은 두지 않는다 (RDS가 먼저 바깥으로 연결할 일이 없다)

resource "aws_db_parameter_group" "main" {
  name        = local.name
  family      = "mysql8.4"
  description = "SnipText: utf8mb4, Asia/Seoul"

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_0900_ai_ci"
  }

  parameter {
    name  = "time_zone"
    value = "Asia/Seoul"
  }

  tags = { Name = local.name }
}

# 마스터 비밀번호: 실행할 때만 만드는 임시 난수. 상태 파일과 plan 파일에 남지 않는다
# RDS가 허용하지 않는 / @ " 공백은 뺀다
ephemeral "random_password" "db_master" {
  length           = 32
  special          = true
  override_special = "!#$%^&*()-_=+[]{}<>:?"
}

resource "aws_db_instance" "main" {
  identifier = local.name

  engine                     = "mysql"
  engine_version             = "8.4" # 주 버전만 고정, 부 버전은 유지 관리 시간에 자동으로 올린다
  auto_minor_version_upgrade = true
  instance_class             = var.db_instance_class
  parameter_group_name       = aws_db_parameter_group.main.name

  allocated_storage = var.db_allocated_storage_gb
  storage_type      = "gp3"
  storage_encrypted = true

  username = var.db_master_username
  # 쓰기 전용 인자: 버전 숫자를 올릴 때만 새 값을 보낸다
  password_wo         = ephemeral.random_password.db_master.result
  password_wo_version = var.db_master_password_version

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false
  availability_zone      = var.availability_zones[0] # 서버와 같은 가용 영역 (영역 간 전송 요금 없음)

  # 시각은 UTC. 백업 02:00~02:30, 유지 관리 화요일 03:00~04:00 (한국 시간). 04:00 토큰 정리 배치와 겹치지 않게 한다
  backup_retention_period = var.db_backup_retention_days
  backup_window           = "17:00-17:30"
  maintenance_window      = "mon:18:00-mon:19:00"
  copy_tags_to_snapshot   = true

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name}-final"
  delete_automated_backups  = false # 인스턴스를 지워도 자동 백업은 보존 기간까지 남긴다

  # 설정 변경은 다음 유지 관리 시간에 반영한다. 바로 바꿔야 하면 그때만 true로 둔다
  apply_immediately = false

  tags = { Name = local.name }

  lifecycle {
    prevent_destroy = true
  }
}
