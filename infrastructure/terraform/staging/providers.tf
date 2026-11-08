# Credentials come from the human's AWS profile (AWS_PROFILE); none is written here.
provider "aws" {
  region = local.region

  default_tags {
    tags = {
      Project     = "mbia"
      Environment = "staging"
      ManagedBy   = "terraform"
    }
  }
}
