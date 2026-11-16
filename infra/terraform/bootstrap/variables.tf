variable "region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
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
