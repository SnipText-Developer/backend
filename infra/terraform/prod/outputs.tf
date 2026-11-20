output "name_servers" {
  description = "가비아 sniptext.kr 네임서버에 넣을 값 4개"
  value       = aws_route53_zone.main.name_servers
}

output "api_fqdn" {
  description = "API 도메인"
  value       = local.api_fqdn
}

output "public_ip" {
  description = "서버 고정 IP"
  value       = aws_eip.app.public_ip
}

output "instance_id" {
  description = "서버 인스턴스 ID (SSM 세션 접속: aws ssm start-session --target <값>)"
  value       = aws_instance.app.id
}

output "instance_name_tag" {
  description = "배포 워크플로(#93)가 SSM 명령 대상을 찾는 Name 태그"
  value       = local.name
}

output "deploy_bucket_name" {
  description = "배포 파일 버킷 (#93 GitHub 환경 변수)"
  value       = aws_s3_bucket.deploy.bucket
}

output "db_address" {
  description = "RDS 주소 (SSM 포트 포워딩 대상)"
  value       = aws_db_instance.main.address
}

output "parameter_prefix" {
  description = "앱 설정·비밀 파라미터 경로"
  value       = local.parameter_prefix
}

output "vpc_id" {
  description = "VPC ID (#91 RDS)"
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "공개 서브넷 2개 (#91 RDS 서브넷 그룹)"
  value       = aws_subnet.public[*].id
}

output "app_security_group_id" {
  description = "서버 보안 그룹 (#91 RDS 보안 그룹이 이 그룹에서 오는 3306만 허용)"
  value       = aws_security_group.app.id
}
