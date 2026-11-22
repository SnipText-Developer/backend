# /sniptext/prod/<이름> 파라미터. deploy.sh(#92)가 서버의 /opt/sniptext/.env로 옮긴다 (설계 문서 5절)
#
# 비밀값은 모두 쓰기 전용 인자(value_wo)로 만든다. 일반 value에 ignore_changes를 걸어도
# refresh 때 공급자가 실제 값을 복호화해 상태 파일에 적기 때문이다. value_wo는 상태에 값을 남기지 않는다

locals {
  parameter_prefix       = "/sniptext/prod"
  admin_parameter_prefix = "/sniptext/admin"

  # 비밀이 아닌 설정: 테라폼이 값까지 넣는다
  app_settings = {
    SPRING_PROFILES_ACTIVE = var.spring_profile
    DB_URL                 = "jdbc:mysql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${var.app_db_schema}?sslMode=REQUIRED"
    DB_USERNAME            = var.app_db_username
    API_DOMAIN             = local.api_fqdn
    GOOGLE_CLIENT_ID       = var.google_client_id
    GOOGLE_REDIRECT_URIS   = join(",", var.google_redirect_uris)
    CORS_ALLOWED_ORIGINS   = join(",", var.cors_allowed_origins)
    AUTH_COOKIE_SAME_SITE  = var.auth_cookie_same_site
    AI_PROVIDER            = "anthropic"
    AI_MODEL               = var.ai_model
  }

  # 사람이 aws ssm put-parameter --overwrite로 실제 값을 넣는 비밀. 테라폼은 CHANGE_ME 자리만 만든다
  # deploy.sh는 CHANGE_ME가 남아 있으면 기동하지 않는다
  app_secrets = toset([
    "DB_PASSWORD",
    "JWT_SECRET",
    "CONTENT_ENCRYPTION_KEY",
    "GOOGLE_CLIENT_SECRET",
    "AI_API_KEY",
  ])
}

resource "aws_ssm_parameter" "setting" {
  for_each = local.app_settings

  name  = "${local.parameter_prefix}/${each.key}"
  type  = "String"
  value = each.value
}

resource "aws_ssm_parameter" "secret" {
  for_each = local.app_secrets

  name             = "${local.parameter_prefix}/${each.key}"
  type             = "SecureString"
  value_wo         = "CHANGE_ME"
  value_wo_version = 1 # 올리면 사람이 넣은 값이 CHANGE_ME로 덮인다. 바꾸지 않는다
}

# 관리 작업 전용. 서버 역할이 읽을 수 있는 /sniptext/prod 밖에 둔다
# (prod 아래에 두면 서버가 털렸을 때 경로 조회로 마스터 비밀번호까지 꺼낼 수 있다. Deny는 경로 재귀 조회를 막지 못한다)
# 이름 지정 조회는 ec2.tf의 DenyAdminParameters가 막는다
# 경로를 바꾸면 파라미터가 새 난수로 다시 만들어지므로 db_master_password_version도 함께 올려 RDS와 맞춘다
resource "aws_ssm_parameter" "db_master_password" {
  name             = "${local.admin_parameter_prefix}/DB_MASTER_PASSWORD"
  description      = "RDS master password (admin only, not for the app)"
  type             = "SecureString"
  value_wo         = ephemeral.random_password.db_master.result
  value_wo_version = var.db_master_password_version
}
