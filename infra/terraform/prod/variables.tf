variable "region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "domain_name" {
  description = "Route 53 호스팅 영역 도메인"
  type        = string
  default     = "sniptext.kr"
}

variable "api_subdomain" {
  description = "API 서버 하위 도메인 (api → api.sniptext.kr)"
  type        = string
  default     = "api"
}

variable "instance_type" {
  description = "EC2 인스턴스 크기 (ARM). 스왑이 계속 쓰이면 올린다"
  type        = string
  default     = "t4g.small"
}

variable "root_volume_size_gb" {
  description = "EC2 루트 볼륨 크기(GB, gp3)"
  type        = number
  default     = 20
}

variable "vpc_cidr" {
  description = "VPC 주소 범위"
  type        = string
  default     = "10.20.0.0/16"
}

variable "availability_zones" {
  description = "공개 서브넷을 둘 가용 영역 2개 (RDS 서브넷 그룹 요건). 첫 번째에 EC2를 둔다"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]

  validation {
    condition     = length(var.availability_zones) == 2
    error_message = "availability_zones는 2개여야 합니다."
  }
}

variable "deploy_file_retention_days" {
  description = "배포 버킷의 커밋별 배포 파일 보존 기간(일). 되돌리기는 이 기간 안의 커밋으로만 된다"
  type        = number
  default     = 90
}
