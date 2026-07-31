locals {
  image_repositories = toset(["backend", "frontend"])
}

resource "aws_ecr_repository" "application" {
  for_each = local.image_repositories

  name                 = "${var.project_name}-${each.key}"
  image_tag_mutability = "IMMUTABLE_WITH_EXCLUSION"
  force_delete         = false

  image_tag_mutability_exclusion_filter {
    filter      = "latest"
    filter_type = "WILDCARD"
  }

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "application" {
  for_each = aws_ecr_repository.application

  repository = each.value.name
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after ${var.untagged_expire_days} days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = var.untagged_expire_days
        }
        action = {
          type = "expire"
        }
      },
      {
        rulePriority = 2
        description  = "Retain the most recent ${var.tagged_image_count} tagged images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.tagged_image_count
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}
