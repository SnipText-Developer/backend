terraform {
  # 쓰기 전용 인자(*_wo)를 #91에서 쓰므로 모든 루트 모듈을 1.11 이상으로 맞춘다
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # bootstrap 자신의 상태도 이 디렉터리가 만든 상태 버킷에 둔다 (로컬 파일을 잃지 않게)
  # 버킷 이름에는 계정 ID가 들어가 저장소에 쓰지 않고 init 때 넘긴다:
  #   terraform init -backend-config="bucket=sniptext-tfstate-<계정 ID>"
  # 버킷이 아직 없는 새 계정에서 처음 만들 때는 README "새 계정에서 처음 만들 때" 절차를 따른다
  backend "s3" {
    key          = "bootstrap/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
