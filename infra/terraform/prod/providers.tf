provider "aws" {
  region = var.region

  default_tags {
    tags = {
      project      = "sniptext"
      env          = "prod"
      "managed-by" = "terraform"
    }
  }
}

# 계정 ID는 변수로 받지 않고 현재 자격에서 읽는다 (계정을 옮겨도 코드 그대로)
data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  name       = "sniptext-prod"
  api_fqdn   = "${var.api_subdomain}.${var.domain_name}"
}
