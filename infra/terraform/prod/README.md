# 테라폼 본체 (prod)

운영 서버 한 대가 놓일 네트워크·EC2·고정 IP·DNS·배포 버킷과 RDS·파라미터 저장소를 만든다.
상태는 bootstrap이 만든 버킷의 `prod/terraform.tfstate`에 있다.

| 리소스 | 내용 |
| --- | --- |
| VPC | 공개 서브넷 2개(가용 영역 2개), 인터넷 게이트웨이. NAT 게이트웨이 없음 |
| 보안 그룹 | 들어오는 것은 80·443만. 22번 없음 |
| EC2 | `t4g.small`, Amazon Linux 2023 ARM, gp3 20GB 암호화, IMDSv2 필수, SSM 세션 관리자 |
| 고정 IP | EC2에 연결 |
| Route 53 | `sniptext.kr` 호스팅 영역(삭제 방지), `api` A 레코드 → 고정 IP |
| S3 배포 버킷 | `sniptext-deploy-<계정 ID>`, 공개 차단, 90일 지난 배포 파일 삭제 |
| RDS | MySQL 8.4 `db.t4g.micro`, 단일 AZ, gp3 20GB 암호화(50GB까지 자동 확장), 공개 접근 끔, 서버 보안 그룹에서만 3306, TLS 접속만 허용, 삭제 방지·최종 스냅샷, 자동 백업 7일. `utf8mb4_0900_ai_ci`, `time_zone=Asia/Seoul` |
| 파라미터 저장소 | `/sniptext/prod/*`: 설정 9개는 값까지, 비밀 5개는 `CHANGE_ME` 자리만. `/sniptext/admin/DB_MASTER_PASSWORD`는 난수(서버 역할은 조회 거부) |
| GitHub 배포 역할 | OIDC 공급자 + `sniptext-prod-github-deploy`. 이 저장소의 `prod` 환경 작업만 위임. 권한은 배포 파일 올리기·운영 서버 명령·결과 조회뿐 (#93) |

## 실행 (파워셸)

`terraform.tfvars.example`을 `terraform.tfvars`로 복사하고 `google_client_id`를 넣는다(커밋하지 않음).

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

## RDS 접속 (SSM 포트 포워딩)

RDS는 바깥에서 닿지 않는다. 서버를 거쳐 로컬 `13306`으로 연다. 세션 관리자 플러그인이 필요하다.

```powershell
aws ssm start-session --target (terraform output -raw instance_id) `
  --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters "host=$(terraform output -raw db_address),portNumber=3306,localPortNumber=13306"
```

다른 창에서 마스터 비밀번호를 꺼내 접속한다(화면·기록에 남기지 않게 바로 클립보드로).

```powershell
aws ssm get-parameter --name /sniptext/admin/DB_MASTER_PASSWORD --with-decryption --query Parameter.Value --output text | Set-Clipboard
mysql -h 127.0.0.1 -P 13306 -u sniptext_admin -p
```

## 앱 전용 DB 계정 (RDS 생성 뒤 한 번)

1. 위처럼 마스터로 접속해 `sql/create_app_user.sql`을 실행한다.
2. 결과의 `generated password`를 복사해 바로 넣는다(다시 볼 수 없다). 명령 기록에 남지 않게 클립보드에서 읽는다.
   ```powershell
   aws ssm put-parameter --name /sniptext/prod/DB_PASSWORD --type SecureString --overwrite --value (Get-Clipboard)
   ```
   다른 비밀값도 같은 방식으로 넣는다.
3. `sniptext_app`으로 다시 접속해 `SHOW DATABASES;`에 `sniptext_dev`와 시스템 스키마(`information_schema`, `performance_schema`. MySQL 8은 모든 계정에 일부 성능 표를 보여 준다)만 보이고, `USE mysql;`·다른 스키마 생성·`DROP USER`가 거부되는지 확인한다.

## 비밀값 넣기 (사람)

테라폼은 아래 5개를 `CHANGE_ME`로만 만든다. 실제 값은 `put-parameter --overwrite`로 넣는다. 테라폼은 이 값을 다시 덮지 않고 상태 파일에도 적지 않는다.

| 이름 | 값 |
| --- | --- |
| `DB_PASSWORD` | 위 앱 계정 비밀번호 |
| `JWT_SECRET` | 32바이트 이상 난수. 개발 서버와 운영 값은 다르게 |
| `CONTENT_ENCRYPTION_KEY` | Base64 32바이트. **잃으면 저장된 본문을 복구할 수 없다** → 따로 안전하게 보관 |
| `GOOGLE_CLIENT_SECRET` | 구글 클라우드 콘솔 |
| `AI_API_KEY` | Anthropic 콘솔 |

```powershell
# 예: 키 만들기 (파워셸)
$b = [byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
```

남은 `CHANGE_ME` 확인:

```powershell
aws ssm get-parameters-by-path --path /sniptext/prod --with-decryption --query "Parameters[?Value=='CHANGE_ME'].Name"
```

## 배포 역할 apply 뒤 (#93)

GitHub 저장소 → Settings → Environments → `prod`의 **Variables**(Secrets 아님)에 넣는다.

| 변수 | 값 |
| --- | --- |
| `AWS_DEPLOY_ROLE_ARN` | `terraform output -raw github_deploy_role_arn` |
| `AWS_REGION` | `ap-northeast-2` |
| `DEPLOY_BUCKET` | `terraform output -raw deploy_bucket_name` |

계정에 GitHub OIDC 공급자가 이미 있으면 apply가 실패한다. 그때는 만들지 말고 가져온다:
`terraform import aws_iam_openid_connect_provider.github arn:aws:iam::<계정 ID>:oidc-provider/token.actions.githubusercontent.com`

## 알아 둘 것

- 비밀 파라미터는 쓰기 전용 인자(`value_wo`)로 만든다. 일반 `value`에 `ignore_changes`를 걸어도 refresh 때 실제 값이 복호화돼 상태 파일에 적히기 때문이다.
- 마스터 비밀번호를 바꾸려면 `db_master_password_version`을 올려 apply한다. RDS와 `DB_MASTER_PASSWORD`가 같은 새 난수로 바뀐다. 마스터 비밀번호 변경은 `apply_immediately`와 상관없이 바로 반영된다.
- 마스터 비밀번호는 `/sniptext/prod` 밖(`/sniptext/admin`)에 두고, 서버 역할 정책에서 `/sniptext/admin` 조회를 명시적으로 거부한다(`ec2.tf`의 `DenyAdminParameters`). 서버에 붙은 `AmazonSSMManagedInstanceCore`가 `ssm:GetParameter`를 모든 파라미터에 허용하기 때문에, 경로만 옮겨서는 이름을 지정한 조회가 막히지 않는다.
- 저장 공간은 남은 공간이 10% 아래로 내려가면 `db_max_allocated_storage_gb`(50GB)까지 자동으로 늘어난다. 늘어난 만큼만 요금이 붙고, 한 번 늘어나면 줄일 수 없다.
- RDS는 `require_secure_transport = 1`이라 평문 접속을 거부한다. 앱은 `DB_URL`의 `sslMode=REQUIRED`로 접속한다. `mysql` 명령도 기본이 TLS라 그대로 접속된다.
- RDS 설정 변경은 다음 유지 관리 시간(화 03:00~04:00 KST)에 반영된다(`apply_immediately = false`).
- RDS는 삭제 방지와 `prevent_destroy`가 둘 다 걸려 있다. 지우려면 두 가지를 먼저 풀어야 한다.

- AMI는 최신 Amazon Linux 2023을 읽지만 `ignore_changes`로 묶어, 이미지가 갱신돼도 인스턴스를 교체하지 않는다. OS 갱신은 서버 안 `dnf upgrade`로 한다.
- `user_data`는 최초 부팅에만 실행된다. 바꿔도 이미 뜬 서버에는 적용되지 않는다(`user_data_replace_on_change = false`). 새 내용을 쓰려면 인스턴스를 교체해야 한다.
- 인스턴스를 교체하면 서버 안의 Caddy 인증서도 사라져 다시 발급받는다. 발급 횟수 제한(같은 도메인 주 5회)에 주의한다.
