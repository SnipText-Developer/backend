# VPC 1개, 공개 서브넷 2개, 인터넷 게이트웨이. NAT 게이트웨이는 비용(월 $40 이상) 때문에 두지 않는다
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = local.name }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = local.name }
}

# EC2는 고정 IP를 붙이므로 자동 공인 IP는 주지 않는다 (공인 IPv4는 개수만큼 과금)
resource "aws_subnet" "public" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.main.id
  availability_zone       = var.availability_zones[count.index]
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  map_public_ip_on_launch = false

  tags = { Name = "${local.name}-public-${count.index}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${local.name}-public" }
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# 기본 보안 그룹은 규칙을 모두 비워 실수로 쓰이지 않게 한다
resource "aws_default_security_group" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${local.name}-default-unused" }
}

# 서버 보안 그룹: 들어오는 것은 80·443만. 22번은 열지 않는다 (접속은 SSM 세션 관리자)
resource "aws_security_group" "app" {
  name        = "${local.name}-app"
  description = "SnipText API server: HTTP/HTTPS in, all out"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-app" }
}

resource "aws_vpc_security_group_ingress_rule" "app" {
  for_each = toset(["80", "443"])

  security_group_id = aws_security_group.app.id
  description       = "public ${each.key}"
  ip_protocol       = "tcp"
  from_port         = tonumber(each.key)
  to_port           = tonumber(each.key)
  cidr_ipv4         = "0.0.0.0/0"
}

# 나가는 것은 모두 허용: 이미지 받기(GHCR), SSM·S3 호출, 인증서 발급, 외부 API(구글·AI)
resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  description       = "all outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
