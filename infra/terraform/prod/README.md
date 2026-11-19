# 테라폼 본체 (prod)

운영 서버 한 대가 놓일 네트워크·EC2·고정 IP·DNS·배포 버킷을 만든다. RDS와 파라미터 저장소는 #91에서 같은 디렉터리에 추가한다.
상태는 bootstrap이 만든 버킷의 `prod/terraform.tfstate`에 있다.

| 리소스 | 내용 |
| --- | --- |
| VPC | 공개 서브넷 2개(가용 영역 2개), 인터넷 게이트웨이. NAT 게이트웨이 없음 |
| 보안 그룹 | 들어오는 것은 80·443만. 22번 없음 |
| EC2 | `t4g.small`, Amazon Linux 2023 ARM, gp3 20GB 암호화, IMDSv2 필수, SSM 세션 관리자 |
| 고정 IP | EC2에 연결 |
| Route 53 | `sniptext.kr` 호스팅 영역(삭제 방지), `api` A 레코드 → 고정 IP |
| S3 배포 버킷 | `sniptext-deploy-<계정 ID>`, 공개 차단, 90일 지난 배포 파일 삭제 |

## 실행 (파워셸)

```powershell
cd infra/terraform/prod

aws configure export-credentials --profile sniptext --format powershell | Invoke-Expression
$env:AWS_REGION = "ap-northeast-2"

$acct = aws sts get-caller-identity --query Account --output text
terraform init "-backend-config=bucket=sniptext-tfstate-$acct"
terraform fmt -check
terraform validate
terraform plan -out tfplan      # 삭제·교체가 있으면 멈춘다
terraform apply tfplan
```

## apply 뒤

1. **가비아 네임서버 변경**: `terraform output name_servers`의 4개를 가비아 → My가비아 → 도메인 통합 관리툴 → `sniptext.kr` → 네임서버 설정에 넣는다. 반영까지 최대 하루.
2. 확인
   ```powershell
   aws ssm start-session --target (terraform output -raw instance_id)
   # 서버 안에서
   docker version; docker compose version; swapon --show; ls -ld /opt/sniptext
   ```
   ```powershell
   nslookup api.sniptext.kr 8.8.8.8     # 네임서버 위임 뒤 고정 IP가 나와야 한다
   ```

## 알아 둘 것

- AMI는 최신 Amazon Linux 2023을 읽지만 `ignore_changes`로 묶어, 이미지가 갱신돼도 인스턴스를 교체하지 않는다. OS 갱신은 서버 안 `dnf upgrade`로 한다.
- `user_data`는 최초 부팅에만 실행된다. 바꿔도 이미 뜬 서버에는 적용되지 않는다(`user_data_replace_on_change = false`). 새 내용을 쓰려면 인스턴스를 교체해야 한다.
- 인스턴스를 교체하면 서버 안의 Caddy 인증서도 사라져 다시 발급받는다. 발급 횟수 제한(같은 도메인 주 5회)에 주의한다.
