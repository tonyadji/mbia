# GitHub Actions deployment (ADR-010, plan §3.2): OIDC, no long-lived key. Used by the workflow of PR-68.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

resource "aws_iam_role" "deploy" {
  name                 = "${local.name}-deploy"
  description          = "GitHub Actions deployment of staging, main branch of tonyadji/mbia only"
  assume_role_policy   = data.aws_iam_policy_document.deploy_assume.json
  max_session_duration = 3600
}

# A workflow run on main, triggered by hand (workflow_dispatch) or not: the sub claim is the same. The
# deployment workflow (PR-68) is the only one that asks for this role.
data "aws_iam_policy_document" "deploy_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.github_deploy_subject]
    }
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "${local.name}-deploy"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}

# Plan §3.2, nothing more.
data "aws_iam_policy_document" "deploy" {
  # GetAuthorizationToken has no resource-level permission.
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
    ]
    resources = [for repository in aws_ecr_repository.images : repository.arn]
  }

  # aws s3 sync --delete: list, upload, remove old files.
  statement {
    sid       = "FrontendList"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.frontend.arn]
  }

  statement {
    sid       = "FrontendWrite"
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]
  }

  statement {
    sid       = "Invalidate"
    actions   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
    resources = [aws_cloudfront_distribution.frontend.arn]
  }

  statement {
    sid     = "RunOnTheVm"
    actions = ["ssm:SendCommand"]
    resources = [
      aws_instance.vm.arn,
      "arn:aws:ssm:${local.region}::document/AWS-RunShellScript",
    ]
  }

  # GetCommandInvocation has no resource-level permission.
  statement {
    sid       = "ReadCommandResult"
    actions   = ["ssm:GetCommandInvocation"]
    resources = ["*"]
  }
}
