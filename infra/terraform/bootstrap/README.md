# 테라폼 bootstrap

본체 테라폼(`infra/terraform/` 아래, #90~)이 쓸 **상태 저장 버킷**과 **월 예산 경보**를 만든다. 계정마다 한 번만 만든다.

| 리소스 | 내용 |
| --- | --- |
| S3 `sniptext-tfstate-<계정 ID>` | 버전 관리, SSE-S3 암호화, 공개 차단, HTTPS 아닌 요청 거절, 삭제 방지(`prevent_destroy`), 이전 버전 90일 뒤 정리(최근 10개 유지) |
| Budgets `sniptext-monthly` | 월 $60(크레딧·환불 제외), 실제 80%·100%, 예측 100%에서 메일 |

- 잠금은 S3 네이티브 잠금(`use_lockfile`)을 쓴다. DynamoDB 테이블은 없다.
- bootstrap 자신의 상태도 이 버킷의 `bootstrap/terraform.tfstate`에 둔다. 로컬 파일을 잃어 테라폼이 리소스를 못 알아보는 일을 막고, 버킷의 버전 관리로 이전 상태도 남는다.
- 상태에는 예산 메일 주소가 들어 있다. 버킷은 공개 차단돼 있지만 상태 파일을 내려받아 공유하지 않는다.
- 버킷 이름에 계정 ID가 들어가 저장소에 쓰지 않고, `init` 때 `-backend-config`로 넘긴다.

## 준비

- 테라폼 1.11 이상, AWS CLI 2.32 이상
- `sniptext-admin`으로 `aws login --profile sniptext` (장기 접근 키를 만들지 않는다)
- 예산 메일은 환경 변수로만 넣는다: `TF_VAR_budget_email`

## 변경할 때 (파워셸)

```powershell
cd infra/terraform/bootstrap

# 임시 자격을 환경 변수로 꺼낸다 (만료되면 aws login부터 다시)
aws configure export-credentials --profile sniptext --format powershell | Invoke-Expression
$env:AWS_REGION = "ap-northeast-2"
$env:TF_VAR_budget_email = "받을주소@example.com"   # 사용자 환경 변수에 있으면 생략

$acct = aws sts get-caller-identity --query Account --output text
terraform init "-backend-config=bucket=sniptext-tfstate-$acct"
terraform fmt -check
terraform validate
terraform plan -out tfplan      # 결과 확인. 삭제·교체가 있으면 멈춘다
terraform apply tfplan
```

맥·리눅스(bash)

```bash
cd infra/terraform/bootstrap

eval "$(aws configure export-credentials --profile sniptext --format env)"
export AWS_REGION=ap-northeast-2
export TF_VAR_budget_email="받을주소@example.com"   # 셸 설정 파일에 있으면 생략

acct=$(aws sts get-caller-identity --query Account --output text)
terraform init -backend-config="bucket=sniptext-tfstate-$acct"
terraform fmt -check
terraform validate
terraform plan -out tfplan
terraform apply tfplan
```

## 새 계정에서 처음 만들 때

버킷이 아직 없어 S3 backend로 시작할 수 없다. 로컬 상태로 만든 뒤 버킷으로 옮긴다.

```powershell
# 1) 이번만 로컬 backend로 덮어쓴다 (*_override.tf는 .gitignore 대상)
Set-Content -Encoding ascii backend_override.tf 'terraform { backend "local" {} }'
terraform init
terraform plan -out tfplan
terraform apply tfplan

# 2) 덮어쓰기를 지우고 상태를 버킷으로 옮긴다
Remove-Item backend_override.tf
$acct = aws sts get-caller-identity --query Account --output text
terraform init -migrate-state "-backend-config=bucket=sniptext-tfstate-$acct"
terraform state list            # 버킷에서 읽은 목록 확인
terraform plan                  # 변경 없음이 나와야 한다

# 3) 로컬에 남은 상태 파일을 지운다 (메일 주소가 들어 있다)
Remove-Item terraform.tfstate, terraform.tfstate.backup, tfplan
```

맥·리눅스(bash)

```bash
# 1)
echo 'terraform { backend "local" {} }' > backend_override.tf
terraform init
terraform plan -out tfplan
terraform apply tfplan

# 2)
rm backend_override.tf
acct=$(aws sts get-caller-identity --query Account --output text)
terraform init -migrate-state -backend-config="bucket=sniptext-tfstate-$acct"
terraform state list
terraform plan

# 3)
rm terraform.tfstate terraform.tfstate.backup tfplan
```

apply 뒤 할 일
- **받는 주소를 인증해야 경보 메일이 온다.** 인증 전에는 알림이 발동(ALARM)해도 메일이 오지 않는다(2026-11 확인). 콘솔 **AWS User Notifications → 전송 채널 → 이메일**에서 해당 주소가 "대기 중"이면 **확인 재전송** → 받은 메일의 **Verify email**(콘솔에 로그인된 브라우저에서) → 상태가 "활성"인지 확인한다. 인증 메일은 스팸함으로 갈 수 있다.
- 경보 메일 발신 주소 `budgets@costalerts.amazonaws.com`이 스팸으로 가지 않게 허용해 둔다. 네이버 메일로는 인증 전 시험에서 받지 못해 지메일을 쓴다.
- 메일이 실제로 오는지는 한도를 이번 달 사용액보다 낮게 잠깐 바꿔 확인한다. 파일은 고치지 않는다.
  1. `terraform apply "-var=budget_limit_usd=10"` (bash는 `-var=budget_limit_usd=10`)
  2. 알림 상태는 apply 직후 ALARM이 되고, 인증된 주소라면 몇 분 안에 메일이 온다
     (`aws budgets describe-notifications-for-budget --account-id <계정 ID> --budget-name sniptext-monthly`로 상태 확인)
  3. `terraform apply`로 원래 한도($60)로 되돌린다. 상태는 바로 OK로 돌아와 이후 경보도 정상으로 온다
  - 다시 시험하려면 한도를 올렸다가(OK) 다시 낮춘다(ALARM). 알림은 상태가 바뀔 때 한 번만 보낸다.

## 본체에서 쓰는 법 (#90~)

```hcl
terraform {
  backend "s3" {
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
```

bootstrap과 같이 `terraform init "-backend-config=bucket=sniptext-tfstate-$acct"`로 버킷을 넘긴다.

## 공급자 잠금 파일

`.terraform.lock.hcl`은 커밋한다. 공급자 버전을 올리면 여러 운영체제 해시를 다시 넣는다.

```powershell
terraform providers lock -platform=windows_amd64 -platform=linux_amd64 -platform=darwin_arm64 -platform=darwin_amd64
```

## 상태가 망가졌을 때

- 잘못된 apply 등으로 상태가 틀어지면 버킷의 이전 버전(`bootstrap/terraform.tfstate`)을 되살린다.
- 상태를 통째로 잃었다면 리소스는 그대로 두고 import로 다시 연결한다.

```powershell
$bucket = "sniptext-tfstate-<계정 ID>"
terraform import aws_s3_bucket.tfstate $bucket
terraform import aws_s3_bucket_versioning.tfstate $bucket
terraform import aws_s3_bucket_server_side_encryption_configuration.tfstate $bucket
terraform import aws_s3_bucket_public_access_block.tfstate $bucket
terraform import aws_s3_bucket_ownership_controls.tfstate $bucket
terraform import aws_s3_bucket_policy.tfstate $bucket
terraform import aws_s3_bucket_lifecycle_configuration.tfstate $bucket
terraform import aws_budgets_budget.monthly "<계정 ID>:sniptext-monthly"
terraform plan    # 변경 없음이 나와야 한다
```

## 지울 때

1. 본체(#90~)를 먼저 destroy한다. 본체 상태가 이 버킷에 있다.
2. 상태를 로컬로 되돌린다: `backend_override.tf`(위 절차 1)를 만들고 `terraform init -migrate-state`
3. `state_bucket.tf`의 `prevent_destroy`를 풀고, 버킷 안의 모든 버전을 비운 뒤 `terraform destroy`
