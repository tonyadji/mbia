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

# CloudFront only accepts certificates from ACM in us-east-1.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = {
      Project     = "mbia"
      Environment = "staging"
      ManagedBy   = "terraform"
    }
  }
}
