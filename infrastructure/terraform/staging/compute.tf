# The staging VM (ADR-010): Caddy, Keycloak and the backend under Docker Compose. No SSH: SSM Session Manager only.

# Latest Amazon Linux 2023 for ARM, read at creation; a newer AMI does not replace the VM (ignore_changes).
data "aws_ssm_parameter" "al2023_arm64" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_instance" "vm" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.insecure_value
  instance_type          = "t4g.medium"
  subnet_id              = aws_subnet.public["a"].id
  vpc_security_group_ids = [aws_security_group.vm.id]
  iam_instance_profile   = aws_iam_instance_profile.vm.name
  user_data              = file("${path.module}/vm-init.sh")

  # The first boot installs packages over HTTPS (vm-init.sh): the security group's egress rules exist first.
  depends_on = [aws_vpc_security_group_egress_rule.vm_https]

  disable_api_termination = true

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
    # 2: containers on the Docker bridge reach the instance role's credentials (backend S3 access, ADR-010).
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 30
    encrypted   = true
  }

  tags        = { Name = local.name }
  volume_tags = { Name = local.name }

  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "vm" {
  domain   = "vpc"
  instance = aws_instance.vm.id

  tags = { Name = local.name }
}

resource "aws_iam_instance_profile" "vm" {
  name = "${local.name}-vm"
  role = aws_iam_role.vm.name
}

resource "aws_iam_role" "vm" {
  name               = "${local.name}-vm"
  description        = "Staging VM: media bucket, ECR pull, /mbia/staging parameters, logs, SSM agent"
  assume_role_policy = data.aws_iam_policy_document.vm_assume.json
}

data "aws_iam_policy_document" "vm_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "vm" {
  name   = "${local.name}-vm"
  role   = aws_iam_role.vm.id
  policy = data.aws_iam_policy_document.vm.json
}

# Phase 7 plan §3.2, nothing more.
data "aws_iam_policy_document" "vm" {
  statement {
    sid       = "MediaObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.media.arn}/*"]
  }

  statement {
    sid       = "MediaList"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.media.arn]
  }

  # GetAuthorizationToken has no resource-level permission.
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid       = "EcrPull"
    actions   = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"]
    resources = [for repository in aws_ecr_repository.images : repository.arn]
  }

  statement {
    sid     = "StagingParameters"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = [
      "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.ssm_prefix}",
      "arn:aws:ssm:${local.region}:${local.account_id}:parameter${local.ssm_prefix}/*",
    ]
  }

  # Docker's awslogs driver; the group is created by Terraform (awslogs-create-group false).
  statement {
    sid       = "Logs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.staging.arn}:*"]
  }

  # SSM managed instance: Session Manager, port forwarding and Run Command. Written by hand instead of the
  # AmazonSSMManagedInstanceCore managed policy, which also grants ssm:GetParameter on every parameter.
  # These actions have no resource-level permission.
  statement {
    sid = "SsmAgent"
    actions = [
      "ssm:UpdateInstanceInformation",
      "ssm:ListInstanceAssociations",
      "ssm:DescribeAssociation",
      "ssm:UpdateInstanceAssociationStatus",
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
      "ec2messages:AcknowledgeMessage",
      "ec2messages:DeleteMessage",
      "ec2messages:FailMessage",
      "ec2messages:GetEndpoint",
      "ec2messages:GetMessages",
      "ec2messages:SendReply",
    ]
    resources = ["*"]
  }
}
