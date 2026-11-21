# 배포 워크플로(.github/workflows/deploy.yml)가 AWS에 들어오는 길 (설계 4-3)
# 장기 접근 키 없이, GitHub가 실행마다 발급하는 OIDC 토큰을 이 역할의 임시 권한으로 바꾼다

# ---------- GitHub OIDC 공급자 ----------
# 계정에 같은 주소의 공급자는 하나만 둘 수 있다. 이미 있으면 apply가 실패하므로 import한다
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # 지문(thumbprint)은 넣지 않는다. GitHub 공급자는 AWS가 신뢰하는 인증 기관으로 직접 검증한다
}

# ---------- 배포 역할 ----------
locals {
  github_owner          = split("/", var.github_repository)[0]
  github_repo_name      = split("/", var.github_repository)[1]
  github_deploy_subject = "repo:${local.github_owner}@${var.github_owner_id}/${local.github_repo_name}@${var.github_repository_id}:environment:${var.github_environment}"
}

# 이 저장소의 prod 환경에서 도는 작업만 위임받는다.
# 브랜치가 아니라 환경 기준이라 출시 때(develop → main) 바꾸지 않는다. 어느 브랜치가 prod 환경을 쓸지는 GitHub 환경 설정이 정한다
data "aws_iam_policy_document" "github_deploy_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # 와일드카드(StringLike) 없이 정확히 일치. 다른 저장소·포크·다른 환경·환경 없는 작업은 모두 거부된다
    # sub 형식: repo:<소유자>@<소유자 ID>/<저장소>@<저장소 ID>:environment:<환경> (변하지 않는 식별자 형식)
    # 숫자 ID가 들어가 저장소 이름을 바꾸거나 지운 뒤 같은 이름으로 다시 만든 저장소는 위임받지 못한다
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.github_deploy_subject]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name                 = "${local.name}-github-deploy"
  assume_role_policy   = data.aws_iam_policy_document.github_deploy_assume.json
  max_session_duration = 3600 # 배포 작업 제한 시간(30분)보다 길게
}

# 워크플로가 쓰는 것만: 배포 파일 올리기, 운영 서버에 명령 보내기, 명령 결과 보기
data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid       = "UploadDeployFiles"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.deploy.arn}/*"]
  }

  # SendCommand는 문서와 대상 인스턴스 양쪽에 권한이 있어야 한다.
  # 문서는 셸 실행(AWS-RunShellScript) 하나만. AWS 소유 문서라 ARN에 계정 ID가 없다
  statement {
    sid       = "SendCommandDocument"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ssm:${var.region}::document/AWS-RunShellScript"]
  }

  # 대상은 Name 태그가 운영 서버인 인스턴스만 (워크플로도 같은 태그로 대상을 찾는다)
  statement {
    sid       = "SendCommandToAppInstance"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ec2:${var.region}:${local.account_id}:instance/*"]

    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/Name"
      values   = [local.name]
    }
  }

  # 명령 상태·출력 조회. 이 세 동작은 리소스 단위 권한을 지원하지 않아 "*"만 된다 (읽기 전용)
  statement {
    sid       = "ReadCommandResults"
    actions   = ["ssm:ListCommands", "ssm:ListCommandInvocations", "ssm:GetCommandInvocation"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
