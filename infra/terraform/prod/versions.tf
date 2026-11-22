terraform {
  # 쓰기 전용 인자(*_wo)와 임시(ephemeral) 리소스를 쓴다 (비밀값을 상태 파일에 남기지 않기 위해)
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    # DB 마스터 비밀번호 생성용 임시 난수 (ephemeral random_password는 3.7부터)
    random = {
      source  = "hashicorp/random"
      version = "~> 3.7"
    }
  }

  # 상태는 bootstrap이 만든 버킷에 둔다. 버킷 이름(계정 ID 포함)은 init 때 넘긴다:
  #   terraform init -backend-config="bucket=sniptext-tfstate-<계정 ID>"
  backend "s3" {
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
