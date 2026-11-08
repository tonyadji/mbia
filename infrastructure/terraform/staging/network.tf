# Public subnets only, no NAT gateway (ADR-010): the VM has an Elastic IP, RDS is not publicly accessible.
resource "aws_vpc" "main" {
  cidr_block           = local.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = local.name }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = local.name }
}

resource "aws_subnet" "public" {
  for_each = local.zones

  vpc_id            = aws_vpc.main.id
  availability_zone = each.value
  cidr_block        = cidrsubnet(local.vpc_cidr, 8, index(keys(local.zones), each.key))

  tags = { Name = "${local.name}-public-${each.key}" }
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
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

# The VPC's default security group allows everything between its members: emptied, used by nothing.
resource "aws_default_security_group" "default" {
  vpc_id = aws_vpc.main.id
}

# Phase 7 plan §3.1. Security groups do not filter the Amazon DNS resolver, the instance metadata service
# nor the Amazon Time Sync Service.
resource "aws_security_group" "vm" {
  name        = "${local.name}-vm"
  description = "Staging VM: Caddy on 80 and 443 only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-vm" }
}

resource "aws_vpc_security_group_ingress_rule" "vm_web" {
  for_each = {
    http  = { protocol = "tcp", port = 80 }
    https = { protocol = "tcp", port = 443 }
    http3 = { protocol = "udp", port = 443 }
  }

  security_group_id = aws_security_group.vm.id
  description       = "Internet to Caddy (${each.key})"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = each.value.protocol
  from_port         = each.value.port
  to_port           = each.value.port
}

# AWS APIs (S3, ECR, SSM, CloudWatch Logs), Let's Encrypt and the Caddy image, all over HTTPS.
resource "aws_vpc_security_group_egress_rule" "vm_https" {
  security_group_id = aws_security_group.vm.id
  description       = "AWS APIs, Let's Encrypt, container images (HTTPS)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "vm_smtp" {
  security_group_id = aws_security_group.vm.id
  description       = "SES SMTP with STARTTLS (backend and Keycloak)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 587
  to_port           = 587
}

resource "aws_vpc_security_group_egress_rule" "vm_rds" {
  security_group_id            = aws_security_group.vm.id
  description                  = "PostgreSQL on RDS"
  referenced_security_group_id = aws_security_group.rds.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

# No rule but this one: Terraform removes the default allow-all egress rule of a new security group.
resource "aws_security_group" "rds" {
  name        = "${local.name}-rds"
  description = "Staging RDS: PostgreSQL from the VM only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-rds" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_vm" {
  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL from the VM"
  referenced_security_group_id = aws_security_group.vm.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
