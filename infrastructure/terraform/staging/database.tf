# RDS PostgreSQL (ADR-010): single-AZ, encrypted, not public, backups kept 7 days. The databases mbia and
# keycloak and their users are created by the human (runbook, PR-68), as infrastructure/postgres/init does locally.

resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = [for subnet in aws_subnet.public : subnet.id]
}

# Created by the human before the first apply (README.md). Read without being stored in the state or the plan.
ephemeral "aws_ssm_parameter" "rds_master_password" {
  arn             = "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.rds_master_password_parameter}"
  with_decryption = true
}

resource "aws_db_instance" "main" {
  identifier = local.name

  engine         = "postgres"
  engine_version = "18" # same major version as docker-compose.yml; minor versions are upgraded by AWS
  instance_class = "db.t4g.micro"

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  username = "mbia_admin"
  # Write-only: never in the state. Increase the version after changing the parameter to rotate it.
  password_wo         = ephemeral.aws_ssm_parameter.rds_master_password.value
  password_wo_version = 1

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  availability_zone      = local.zones.a
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period    = 7
  backup_window              = "01:30-02:30"
  maintenance_window         = "sun:03:00-sun:04:00"
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name}-final"

  lifecycle {
    prevent_destroy = true
  }
}
