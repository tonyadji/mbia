# Docker's awslogs driver writes one stream per service (infrastructure/staging/compose.yml).
resource "aws_cloudwatch_log_group" "staging" {
  name              = local.log_group_name
  retention_in_days = 30
}
