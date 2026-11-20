# Amazon Linux 2023 ARM 최신 AMI. 이미지가 갱신돼도 인스턴스가 교체되지 않게 ami는 ignore_changes로 묶는다
data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

# ---------- 인스턴스 역할 ----------
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${local.name}-app"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# SSM 세션 관리자 접속·명령 실행
resource "aws_iam_role_policy_attachment" "app_ssm_core" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# deploy.sh가 쓰는 권한: /sniptext/prod/* 파라미터 읽기, 배포 버킷 읽기
# SecureString은 AWS 관리 키(aws/ssm)로 암호화하므로 kms:Decrypt를 따로 주지 않아도 복호화된다
data "aws_iam_policy_document" "app_runtime" {
  statement {
    sid     = "ReadAppParameters"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = [
      "arn:aws:ssm:${var.region}:${local.account_id}:parameter/sniptext/prod",
      "arn:aws:ssm:${var.region}:${local.account_id}:parameter/sniptext/prod/*",
    ]
  }

  # AmazonSSMManagedInstanceCore가 ssm:GetParameter(s)를 모든 파라미터에 허용하므로,
  # 관리 작업 전용 경로(/sniptext/admin)는 이름 지정 조회를 명시적으로 거부한다.
  # 경로 조회(GetParametersByPath)는 /sniptext/admin·/sniptext에 허용이 없어 이미 막혀 있다
  statement {
    sid     = "DenyAdminParameters"
    effect  = "Deny"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = [
      "arn:aws:ssm:${var.region}:${local.account_id}:parameter${local.admin_parameter_prefix}",
      "arn:aws:ssm:${var.region}:${local.account_id}:parameter${local.admin_parameter_prefix}/*",
    ]
  }

  statement {
    sid       = "ListDeployBucket"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.deploy.arn]
  }

  statement {
    sid       = "ReadDeployFiles"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.deploy.arn}/*"]
  }
}

resource "aws_iam_role_policy" "app_runtime" {
  name   = "runtime"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.app_runtime.json
}

resource "aws_iam_instance_profile" "app" {
  name = "${local.name}-app"
  role = aws_iam_role.app.name
}

# ---------- 인스턴스 ----------
resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.insecure_value # 공개 AMI ID라 plan에 보이게 한다
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.app.name

  # 도커·compose 설치, 스왑 2GB, /opt/sniptext 생성까지만 한다. 앱 실행은 deploy.sh(#92)가 맡는다
  # 윈도우에서 실행해도 CRLF가 섞이지 않게 LF로 맞춘다
  user_data                   = replace(file("${path.module}/user_data.sh"), "\r\n", "\n")
  user_data_replace_on_change = false

  # IMDSv2 필수. 홉 1이라 컨테이너 안에서는 인스턴스 자격을 못 꺼낸다 (앱은 AWS 자격이 필요 없다)
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size_gb
    encrypted             = true
    delete_on_termination = true

    tags = { Name = local.name }
  }

  # 배포 워크플로(#93)가 SSM 명령 대상을 Name 태그로 찾는다
  tags = { Name = local.name }

  lifecycle {
    ignore_changes = [ami]
  }
}

# 고정 IP. 인스턴스가 바뀌어도 DNS를 그대로 둔다
resource "aws_eip" "app" {
  domain   = "vpc"
  instance = aws_instance.app.id

  tags = { Name = local.name }

  depends_on = [aws_internet_gateway.main]
}
