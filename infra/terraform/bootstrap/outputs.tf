output "state_bucket_name" {
  description = "본체 테라폼(#90~)의 backend \"s3\" bucket 값"
  value       = aws_s3_bucket.tfstate.bucket
}

output "region" {
  description = "상태 버킷 리전"
  value       = var.region
}

output "budget_name" {
  description = "예산 이름"
  value       = aws_budgets_budget.monthly.name
}
