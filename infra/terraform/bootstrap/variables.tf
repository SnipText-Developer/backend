variable "region" {
  description = "AWS 리전. backend 블록에는 변수를 쓸 수 없어 versions.tf의 backend region과 같은 값만 허용한다"
  type        = string
  default     = "ap-northeast-2"

  # 다른 리전에 버킷을 만들면 backend가 서울에서 버킷을 찾아 상태 이전이 실패한다.
  # 리전을 바꾸려면 이 검증과 versions.tf의 backend region을 함께 바꾼다
  validation {
    condition     = var.region == "ap-northeast-2"
    error_message = "region은 versions.tf의 backend region(ap-northeast-2)과 같아야 합니다. 바꾸려면 둘을 함께 바꾸세요."
  }
}

variable "budget_limit_usd" {
  description = "월 예산(USD, 크레딧 제외). 예상 운영비 약 $42보다 80% 경보선이 높도록 $60"
  type        = number
  default     = 60

  validation {
    condition     = var.budget_limit_usd > 0
    error_message = "budget_limit_usd는 0보다 커야 합니다."
  }
}

variable "budget_email" {
  description = "예산 경보 받을 메일. 파일에 쓰지 않고 환경 변수 TF_VAR_budget_email로만 넣는다"
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.budget_email))
    error_message = "budget_email 형식이 올바르지 않습니다."
  }
}
