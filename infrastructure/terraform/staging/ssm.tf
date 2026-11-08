# Names of the settings of the VM (infrastructure/staging/backend.env.example and keycloak.env.example).
# Terraform creates each name with the placeholder "change-me" and never touches the value again: the human
# sets the values (runbook, PR-68), which never appear in this repository.
locals {
  ssm_parameters = {
    "backend/SPRING_DATASOURCE_URL"          = "String"
    "backend/SPRING_DATASOURCE_USERNAME"     = "String"
    "backend/SPRING_DATASOURCE_PASSWORD"     = "SecureString"
    "backend/MBIA_SECURITY_ISSUER_URI"       = "String"
    "backend/MBIA_CORS_ALLOWED_ORIGINS"      = "String"
    "backend/MBIA_STORAGE_BUCKET"            = "String"
    "backend/MBIA_STORAGE_REGION"            = "String"
    "backend/MBIA_STORAGE_PATH_STYLE_ACCESS" = "String"
    "backend/MBIA_APP_BASE_URL"              = "String"
    "backend/MBIA_MAIL_FROM"                 = "String"
    "backend/MBIA_SMTP_HOST"                 = "String"
    "backend/MBIA_SMTP_PORT"                 = "String"
    "backend/MBIA_SMTP_USERNAME"             = "SecureString"
    "backend/MBIA_SMTP_PASSWORD"             = "SecureString"
    "backend/MBIA_PROBLEMS_BASE_URI"         = "String"
    "keycloak/KC_HOSTNAME"                   = "String"
    "keycloak/KC_HOSTNAME_ADMIN"             = "String"
    "keycloak/KC_DB_URL"                     = "String"
    "keycloak/KC_DB_USERNAME"                = "String"
    "keycloak/KC_DB_PASSWORD"                = "SecureString"
    "keycloak/KC_BOOTSTRAP_ADMIN_USERNAME"   = "SecureString"
    "keycloak/KC_BOOTSTRAP_ADMIN_PASSWORD"   = "SecureString"
    "keycloak/MBIA_WEB_ORIGIN"               = "String"
    "keycloak/MBIA_KC_SMTP_HOST"             = "String"
    "keycloak/MBIA_KC_SMTP_PORT"             = "String"
    "keycloak/MBIA_KC_SMTP_FROM"             = "String"
    "keycloak/MBIA_KC_SMTP_USERNAME"         = "SecureString"
    "keycloak/MBIA_KC_SMTP_PASSWORD"         = "SecureString"
  }
}

resource "aws_ssm_parameter" "settings" {
  for_each = local.ssm_parameters

  name  = "${local.ssm_prefix}/${each.key}"
  type  = each.value
  value = "change-me"

  lifecycle {
    ignore_changes = [value]
  }
}
