# Values the human needs for the SSM parameters and the runbook. None is secret.
output "vm_instance_id" {
  value = aws_instance.vm.id
}

output "vm_public_ip" {
  value = aws_eip.vm.public_ip
}

output "rds_endpoint" {
  value = aws_db_instance.main.address
}

output "media_bucket" {
  value = aws_s3_bucket.media.bucket
}

# Source of the media bucket in the frontend CSP (infrastructure/staging/README.md §Frontend on CloudFront).
output "media_bucket_origin" {
  value = local.media_origin
}

output "ecr_repository_urls" {
  value = { for name, repository in aws_ecr_repository.images : name => repository.repository_url }
}

output "log_group" {
  value = aws_cloudwatch_log_group.staging.name
}

output "ssm_parameter_names" {
  value = sort([for parameter in aws_ssm_parameter.settings : parameter.name])
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.frontend.id
}

output "deploy_role_arn" {
  value = aws_iam_role.deploy.arn
}

output "ses_smtp_user" {
  value = aws_iam_user.ses_smtp.name
}
