# 랜딩 페이지 호스팅 (#138)
# 랜딩은 대시보드와 같은 주소(sniptext.kr)에서 연다. 별도 버킷·인증서·배포 없이 대시보드 호스팅(dashboard.tf)에 얹는다
# - 빌드 결과는 대시보드 버킷의 landing/ 아래에 둔다 (PUBLIC_URL=/landing으로 빌드해 파일 주소가 /landing/... 로 나온다)
# - 주소는 /dashboard/* = 대시보드(버킷 dashboard/), 나머지 = 랜딩으로 나눈다 (CloudFront 함수, dashboard.tf)
# - 지금은 /dashboard 경로만 열어 두었다. 대시보드가 dashboard/ 로 옮겨 간 뒤 함수를 바꿔 나머지 경로를 랜딩으로 돌린다

locals {
  landing_prefix         = "landing"
  landing_github_subject = "repo:${split("/", var.landing_github_repository)[0]}@${var.github_owner_id}/${split("/", var.landing_github_repository)[1]}@${var.landing_github_repository_id}:environment:${var.github_environment}"
}

# ---------- 랜딩 저장소 배포 역할 ----------
# 랜딩 저장소의 prod 환경에서 도는 워크플로만 위임받는다 (대시보드 배포 역할과 같은 방식)
data "aws_iam_policy_document" "landing_deploy_assume" {
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

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.landing_github_subject]
    }
  }
}

resource "aws_iam_role" "landing_deploy" {
  name               = "${local.name}-landing-deploy"
  assume_role_policy = data.aws_iam_policy_document.landing_deploy_assume.json
}

# 대시보드 버킷의 landing/ 아래만 올리고 지울 수 있다. 대시보드 파일은 건드리지 못한다
data "aws_iam_policy_document" "landing_deploy" {
  statement {
    sid       = "ListLandingPrefix"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.dashboard.arn]

    condition {
      test     = "StringLike"
      variable = "s3:prefix"
      values   = ["${local.landing_prefix}/*"]
    }
  }

  statement {
    sid       = "SyncLandingObjects"
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.dashboard.arn}/${local.landing_prefix}/*"]
  }

  # 무효화 경로는 IAM으로 제한할 수 없어 대시보드 배포 전체에 대한 권한이 된다 (워크플로에서 / 와 /landing/* 만 비운다)
  statement {
    sid       = "InvalidateCache"
    actions   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
    resources = [aws_cloudfront_distribution.dashboard.arn]
  }
}

resource "aws_iam_role_policy" "landing_deploy" {
  name   = "deploy"
  role   = aws_iam_role.landing_deploy.id
  policy = data.aws_iam_policy_document.landing_deploy.json
}
