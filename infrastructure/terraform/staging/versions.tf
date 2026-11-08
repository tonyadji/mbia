terraform {
  # Pinned: the S3 backend's native locking and write-only arguments need Terraform 1.11 or later.
  required_version = "= 1.16.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.69.1"
    }
  }

  # Created once by the bootstrap step (README.md), outside Terraform. Locking: a lock file next to the state.
  backend "s3" {
    bucket       = "lehnade-mbia-terraform-state"
    key          = "staging/terraform.tfstate"
    region       = "eu-west-3"
    encrypt      = true
    use_lockfile = true
  }
}
