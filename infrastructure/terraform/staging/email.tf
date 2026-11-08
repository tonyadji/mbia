# Amazon SES (ADR-010). The identity, its DNS records and the feedback topic were created by hand in the
# console (plan §2.2): they are imported, never recreated, and prevent_destroy makes a plan that would
# destroy or replace one of them fail.

resource "aws_sesv2_email_identity" "staging" {
  email_identity = local.frontend_domain # Easy DKIM, the default: no signing attribute set here

  lifecycle {
    prevent_destroy = true
  }
}

import {
  to = aws_sesv2_email_identity.staging
  id = local.frontend_domain
}

resource "aws_sesv2_email_identity_mail_from_attributes" "staging" {
  email_identity         = aws_sesv2_email_identity.staging.email_identity
  mail_from_domain       = "mail.${local.frontend_domain}"
  behavior_on_mx_failure = "USE_DEFAULT_VALUE"

  lifecycle {
    prevent_destroy = true
  }
}

import {
  to = aws_sesv2_email_identity_mail_from_attributes.staging
  id = local.frontend_domain
}

# The three Easy DKIM tokens, read from the existing identity.
data "aws_sesv2_email_identity" "staging" {
  email_identity = local.frontend_domain
}

locals {
  ses_dkim_tokens = toset(data.aws_sesv2_email_identity.staging.dkim_signing_attributes[0].tokens)

  # imported: published by hand (plan §2.2) under this exact name. The MAIL FROM records were published by hand
  # under a wrong name (mail.mbia-staging.lehnade.com.mbia-staging.lehnade.com): Terraform creates the right
  # ones, and the human deletes the wrong ones (README.md).
  ses_records = {
    mail_from_mx  = { name = "mail.${local.frontend_domain}", type = "MX", ttl = 300, value = "10 feedback-smtp.${local.region}.amazonses.com", imported = false }
    mail_from_spf = { name = "mail.${local.frontend_domain}", type = "TXT", ttl = 300, value = "v=spf1 include:amazonses.com ~all", imported = false }
    dmarc         = { name = "_dmarc.${local.frontend_domain}", type = "TXT", ttl = 300, value = "v=DMARC1; p=none;", imported = true }
  }
}

resource "aws_route53_record" "ses_dkim" {
  for_each = local.ses_dkim_tokens

  zone_id = data.aws_route53_zone.lehnade.zone_id
  name    = "${each.key}._domainkey.${local.frontend_domain}"
  type    = "CNAME"
  ttl     = 1800
  records = ["${each.key}.dkim.amazonses.com"]

  lifecycle {
    prevent_destroy = true
  }
}

import {
  for_each = local.ses_dkim_tokens
  to       = aws_route53_record.ses_dkim[each.key]
  id       = "${data.aws_route53_zone.lehnade.zone_id}_${each.key}._domainkey.${local.frontend_domain}_CNAME"
}

resource "aws_route53_record" "ses" {
  for_each = local.ses_records

  zone_id = data.aws_route53_zone.lehnade.zone_id
  name    = each.value.name
  type    = each.value.type
  ttl     = each.value.ttl
  records = [each.value.value]

  lifecycle {
    prevent_destroy = true
  }
}

import {
  for_each = { for key, record in local.ses_records : key => record if record.imported }
  to       = aws_route53_record.ses[each.key]
  id       = "${data.aws_route53_zone.lehnade.zone_id}_${each.value.name}_${each.value.type}"
}

# Bounces and complaints. Its email subscription stays outside Terraform (a real address).
resource "aws_sns_topic" "ses_feedback" {
  name         = "${local.name}-ses-feedback"
  display_name = "${local.name}-ses-feedback" # as created in the console

  lifecycle {
    prevent_destroy = true
  }
}

import {
  to = aws_sns_topic.ses_feedback
  id = "arn:aws:sns:${local.region}:${local.account_id}:${local.name}-ses-feedback"
}

resource "aws_ses_identity_notification_topic" "feedback" {
  for_each = toset(["Bounce", "Complaint"])

  identity          = aws_sesv2_email_identity.staging.email_identity
  notification_type = each.key
  topic_arn         = aws_sns_topic.ses_feedback.arn

  lifecycle {
    prevent_destroy = true
  }
}

import {
  for_each = toset(["Bounce", "Complaint"])
  to       = aws_ses_identity_notification_topic.feedback[each.key]
  id       = "${local.frontend_domain}|${each.key}"
}

# SMTP credentials of the backend and Keycloak (plan §3.2). Its access key is created by the human
# (runbook, PR-68), never by Terraform: the secret would be stored in the state.
resource "aws_iam_user" "ses_smtp" {
  name = "${local.name}-ses-smtp"
}

resource "aws_iam_user_policy" "ses_smtp" {
  name   = "${local.name}-ses-smtp"
  user   = aws_iam_user.ses_smtp.name
  policy = data.aws_iam_policy_document.ses_smtp.json
}

data "aws_iam_policy_document" "ses_smtp" {
  statement {
    sid       = "SendFromTheStagingDomain"
    actions   = ["ses:SendRawEmail"]
    resources = [aws_sesv2_email_identity.staging.arn]

    condition {
      test     = "StringLike"
      variable = "ses:FromAddress"
      values   = ["*@${local.frontend_domain}"]
    }
  }
}
