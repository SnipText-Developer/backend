terraform {
  # 쓰기 전용 인자(*_wo)를 #91에서 쓴다
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
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
