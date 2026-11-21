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

# ---------- DB (#91) ----------
variable "db_instance_class" {
  description = "RDS 인스턴스 크기"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage_gb" {
  description = "RDS 저장소 크기(GB, gp3)"
  type        = number
  default     = 20
}

variable "db_max_allocated_storage_gb" {
  description = "RDS 저장소 자동 확장 상한(GB). 설정만으로는 요금이 없다"
  type        = number
  default     = 50
}

variable "db_backup_retention_days" {
  description = "RDS 자동 백업 보존 기간(일). 이 기간 안의 시점으로 복구할 수 있다"
  type        = number
  default     = 7
}

variable "db_master_username" {
  description = "RDS 마스터 계정 이름. 관리 작업 전용이고 앱은 쓰지 않는다"
  type        = string
  default     = "sniptext_admin"
}

variable "db_master_password_version" {
  description = "마스터 비밀번호 버전. 올리면 새 난수로 RDS와 DB_MASTER_PASSWORD 파라미터를 함께 바꾼다"
  type        = number
  default     = 3 # 3: /sniptext/admin으로 옮길 때 RDS 변경이 실패해 두 값을 다시 맞춤
}

variable "app_db_schema" {
  description = "앱이 쓰는 스키마. 출시 전 sniptext_dev, 출시 후 sniptext (설계 문서 7절)"
  type        = string
  default     = "sniptext_dev"
}

variable "app_db_username" {
  description = "앱 전용 DB 계정 (sql/create_app_user.sql로 만든다)"
  type        = string
  default     = "sniptext_app"
}

# ---------- 앱 설정 파라미터 (#91) ----------
variable "spring_profile" {
  description = "SPRING_PROFILES_ACTIVE. 출시 전 dev, 출시 후 prod"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "prod"], var.spring_profile)
    error_message = "spring_profile은 dev 또는 prod여야 합니다."
  }
}

variable "google_client_id" {
  description = "구글 OAuth 클라이언트 ID (비밀 아님). terraform.tfvars로 넣는다"
  type        = string

  validation {
    condition     = endswith(var.google_client_id, ".apps.googleusercontent.com")
    error_message = "google_client_id는 .apps.googleusercontent.com으로 끝나야 합니다."
  }
}

variable "google_redirect_uris" {
  description = "허용할 구글 로그인 redirectUri 목록. 출시 전 값(설계 문서 4-10). 출시 때 sniptext.kr만 남긴다"
  type        = list(string)
  default = [
    "https://sniptext.kr/auth/google/callback",
    "http://localhost:3000/auth/google/callback",
  ]
}

variable "cors_allowed_origins" {
  description = "브라우저에서 API를 부를 수 있는 화면 주소(출처) 목록. 출시 전 값(설계 문서 4-10). 출시 때 sniptext.kr만 남긴다"
  type        = list(string)
  default = [
    "https://sniptext.kr",
    "http://localhost:3000",
  ]

  validation {
    # 앱도 기동 때 같은 형식을 검사한다. 자격 증명을 허용하므로 *는 안 되고, 경로·끝 슬래시가 붙으면 브라우저 Origin과 맞지 않는다
    condition     = length(var.cors_allowed_origins) > 0 && alltrue([for o in var.cors_allowed_origins : can(regex("^https?://[A-Za-z0-9.-]+(:[0-9]+)?$", o))])
    error_message = "cors_allowed_origins는 비어 있지 않아야 하고, 각 값은 경로 없이 스킴://호스트[:포트] 형식이어야 합니다 (* 불가)."
  }
}

variable "auth_cookie_same_site" {
  description = "재발급 토큰 쿠키 SameSite. 출시 전 None(로컬 화면 → 서버가 다른 사이트), 출시 후 Lax (설계 문서 4-10)"
  type        = string
  default     = "None"

  validation {
    condition     = contains(["None", "Lax", "Strict"], var.auth_cookie_same_site)
    error_message = "auth_cookie_same_site는 None, Lax, Strict 중 하나여야 합니다."
  }
}

variable "ai_model" {
  description = "AI 톤 교정에 쓸 Anthropic 모델 ID"
  type        = string
  default     = "claude-haiku-5-5"
}

# ---------- GitHub 배포 역할 (#93) ----------
variable "github_repository" {
  description = "배포 역할을 위임받을 GitHub 저장소 (소유자/이름, 대소문자 그대로)"
  type        = string
  default     = "SnipText-Developer/backend"
}

# 저장소가 변하지 않는 식별자 형식(use_immutable_subject)이라 OIDC sub에 소유자·저장소 숫자 ID가 붙는다.
# 확인: gh api repos/<저장소>/actions/oidc/customization/sub → sub_claim_prefix
variable "github_owner_id" {
  description = "GitHub 소유자(조직) 숫자 ID"
  type        = number
  default     = 340844684
}

variable "github_repository_id" {
  description = "GitHub 저장소 숫자 ID. 저장소를 지우고 같은 이름으로 다시 만들면 바뀐다"
  type        = number
  default     = 1431886104
}

variable "github_environment" {
  description = "배포 역할을 위임받을 GitHub 환경. 이 환경을 쓰는 작업만 위임된다"
  type        = string
  default     = "prod"
}
