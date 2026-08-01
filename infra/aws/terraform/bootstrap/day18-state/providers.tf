provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "finrisk-radar"
      Environment = "day18"
      ManagedBy   = "Terraform"
      Stack       = "day18-state"
    }
  }
}

data "aws_caller_identity" "current" {}
