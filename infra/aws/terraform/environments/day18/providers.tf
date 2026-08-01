provider "aws" {
  region = var.aws_region

  default_tags {
    tags = merge(
      {
        Project     = "finrisk-radar"
        Environment = "day18"
        ManagedBy   = "Terraform"
      },
      var.common_tags
    )
  }
}

data "aws_caller_identity" "current" {}
data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ecr_repository" "backend" {
  name = var.backend_repository_name
}

data "aws_ecr_repository" "frontend" {
  name = var.frontend_repository_name
}

data "aws_ecr_image" "backend" {
  repository_name = data.aws_ecr_repository.backend.name
  image_tag       = var.image_tag
}

data "aws_ecr_image" "frontend" {
  repository_name = data.aws_ecr_repository.frontend.name
  image_tag       = var.image_tag
}

data "aws_s3_bucket" "application" {
  bucket = var.application_bucket_name
}
