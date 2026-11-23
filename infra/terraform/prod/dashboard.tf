# 대시보드(프론트 정적 빌드) 호스팅 (#131)
# 비공개 S3 버킷 → CloudFront(HTTPS) → sniptext.kr. 버킷은 CloudFront만 읽을 수 있다 (OAC)

# ---------- 빌드 결과를 두는 버킷 ----------
resource "aws_s3_bucket" "dashboard" {
  bucket = "sniptext-dashboard-${local.account_id}"
}

resource "aws_s3_bucket_public_access_block" "dashboard" {
  bucket = aws_s3_bucket.dashboard.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "dashboard" {
  bucket = aws_s3_bucket.dashboard.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "dashboard" {
  bucket = aws_s3_bucket.dashboard.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

data "aws_iam_policy_document" "dashboard_bucket" {
  # 이 배포(distribution)만 읽기 허용. 다른 CloudFront 배포나 공개 접근은 막힌다
  statement {
    sid       = "AllowCloudFrontRead"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.dashboard.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.dashboard.arn]
    }
  }

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.dashboard.arn,
      "${aws_s3_bucket.dashboard.arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "dashboard" {
  bucket = aws_s3_bucket.dashboard.id
  policy = data.aws_iam_policy_document.dashboard_bucket.json

  depends_on = [aws_s3_bucket_public_access_block.dashboard]
}

# ---------- 인증서 (CloudFront용이라 us-east-1) ----------
resource "aws_acm_certificate" "dashboard" {
  provider          = aws.us_east_1
  domain_name       = var.domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "dashboard_cert_validation" {
  for_each = {
    for o in aws_acm_certificate.dashboard.domain_validation_options : o.domain_name => {
      name   = o.resource_record_name
      type   = o.resource_record_type
      record = o.resource_record_value
    }
  }

  zone_id         = aws_route53_zone.main.zone_id
  name            = each.value.name
  type            = each.value.type
  ttl             = 300
  records         = [each.value.record]
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "dashboard" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.dashboard.arn
  validation_record_fqdns = [for r in aws_route53_record.dashboard_cert_validation : r.fqdn]
}

# ---------- CloudFront ----------
resource "aws_cloudfront_origin_access_control" "dashboard" {
  name                              = "${local.name}-dashboard"
  description                       = "SnipText dashboard bucket: CloudFront only"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# 화면 주소(/templates, /auth/google/callback 등)로 바로 들어오거나 새로고침해도 index.html을 준다.
# 마지막 경로에 점(.)이 있는 요청(/static/js/main.js 등)은 파일 그대로 찾는다.
# 404·403을 index.html로 바꾸는 방식은 없는 파일까지 200으로 숨기므로 쓰지 않는다
resource "aws_cloudfront_function" "dashboard_spa" {
  name    = "${local.name}-dashboard-spa"
  runtime = "cloudfront-js-2.0"
  comment = "Rewrite page routes to /index.html"
  publish = true
  code    = <<-EOT
    function handler(event) {
      var request = event.request;
      var last = request.uri.split('/').pop();
      if (last.indexOf('.') === -1) {
        request.uri = '/index.html';
      }
      return request;
    }
  EOT
}

# AWS 관리형 정책: 압축·긴 캐시(원본의 Cache-Control을 따른다), 기본 보안 헤더(HSTS, nosniff 등)
data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_response_headers_policy" "security_headers" {
  name = "Managed-SecurityHeadersPolicy"
}

resource "aws_cloudfront_distribution" "dashboard" {
  enabled             = true
  comment             = "${local.name} dashboard"
  aliases             = [var.domain_name]
  default_root_object = "index.html"
  is_ipv6_enabled     = true
  http_version        = "http2and3"
  # 한국 포함 지역까지만 (가장 비싼 남미·오세아니아 일부 제외)
  price_class = "PriceClass_200"

  origin {
    origin_id                = "dashboard-s3"
    domain_name              = aws_s3_bucket.dashboard.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.dashboard.id
  }

  default_cache_behavior {
    target_origin_id           = "dashboard-s3"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.security_headers.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.dashboard_spa.arn
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.dashboard.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

# ---------- 대시보드 저장소 배포 역할 ----------
# 대시보드 저장소의 prod 환경에서 도는 워크플로만 위임받는다 (백엔드 배포 역할과 같은 방식, github_deploy.tf)
# 어느 브랜치가 prod 환경을 쓸지는 대시보드 저장소의 GitHub 환경 설정(배포 브랜치 main만)이 정한다
locals {
  dashboard_github_subject = "repo:${split("/", var.dashboard_github_repository)[0]}@${var.github_owner_id}/${split("/", var.dashboard_github_repository)[1]}@${var.dashboard_github_repository_id}:environment:${var.github_environment}"
}

data "aws_iam_policy_document" "dashboard_deploy_assume" {
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
      values   = [local.dashboard_github_subject]
    }
  }
}

resource "aws_iam_role" "dashboard_deploy" {
  name               = "${local.name}-dashboard-deploy"
  assume_role_policy = data.aws_iam_policy_document.dashboard_deploy_assume.json
}

# 빌드 결과 올리기(s3 sync --delete: 목록·올리기·지우기)와 이 배포의 캐시 비우기만
data "aws_iam_policy_document" "dashboard_deploy" {
  statement {
    sid       = "ListDashboardBucket"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.dashboard.arn]
  }

  statement {
    sid       = "SyncDashboardObjects"
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.dashboard.arn}/*"]
  }

  statement {
    sid       = "InvalidateDashboardCache"
    actions   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
    resources = [aws_cloudfront_distribution.dashboard.arn]
  }
}

resource "aws_iam_role_policy" "dashboard_deploy" {
  name   = "deploy"
  role   = aws_iam_role.dashboard_deploy.id
  policy = data.aws_iam_policy_document.dashboard_deploy.json
}

# ---------- sniptext.kr → CloudFront ----------
resource "aws_route53_record" "dashboard" {
  for_each = toset(["A", "AAAA"])

  zone_id = aws_route53_zone.main.zone_id
  name    = var.domain_name
  type    = each.value

  alias {
    name                   = aws_cloudfront_distribution.dashboard.domain_name
    zone_id                = aws_cloudfront_distribution.dashboard.hosted_zone_id
    evaluate_target_health = false
  }
}
