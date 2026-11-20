# 가비아에서 산 도메인의 네임서버를 이 호스팅 영역으로 위임한다 (출력값 name_servers 4개를 가비아에 입력)
resource "aws_route53_zone" "main" {
  name = var.domain_name

  # 지우면 네임서버가 바뀌어 가비아 설정을 다시 해야 하고 반영에 최대 하루가 걸린다
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_route53_record" "api" {
  zone_id = aws_route53_zone.main.zone_id
  name    = local.api_fqdn
  type    = "A"
  ttl     = 300
  records = [aws_eip.app.public_ip]
}
