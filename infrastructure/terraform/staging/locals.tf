# Staging / closed beta environment of ADR-010. Every value is a staging value: production gets its own root module.
locals {
  name   = "mbia-staging"
  region = "eu-west-3"

  frontend_domain = "mbia-staging.lehnade.com"
  api_domain      = "api.${local.frontend_domain}"
  auth_domain     = "auth.${local.frontend_domain}"
  frontend_origin = "https://${local.frontend_domain}"

  vpc_cidr = "10.20.0.0/16"
  # RDS needs a subnet group in two zones; the VM and the database run in the first one.
  zones = { a = "eu-west-3a", b = "eu-west-3b" }

  media_bucket_name    = "lehnade-mbia-staging-media"
  frontend_bucket_name = "lehnade-mbia-staging-frontend"
  log_group_name       = "/mbia/staging"

  # Read by the VM (Phase 7 plan §3.2). The RDS master password lives outside this path: the VM cannot read it.
  ssm_prefix                    = "/mbia/staging"
  rds_master_password_parameter = "/mbia-admin/staging/rds-master-password"

  ecr_repositories = toset(["mbia-backend", "mbia-keycloak"])

  # The only GitHub reference allowed to deploy (plan §3.2): the main branch of this repository. The repository
  # uses GitHub's immutable OIDC subject: owner and repository carry their numeric ids (public, not secret), so a
  # renamed or recreated repository of the same name cannot obtain the role. Read it with:
  #   gh api repos/tonyadji/mbia/actions/oidc/customization/sub   (sub_claim_prefix)
  github_deploy_subject = "repo:tonyadji@19990474/mbia@1387134516:ref:refs/heads/main"

  account_id = data.aws_caller_identity.current.account_id
}

data "aws_caller_identity" "current" {}
