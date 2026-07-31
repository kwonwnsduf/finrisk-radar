variable "aws_region" {
  description = "AWS region in which the ECR repositories are managed."
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "Project prefix used for ECR repository names and tags."
  type        = string
  default     = "finrisk"

  validation {
    condition     = can(regex("^[a-z0-9]+([._-][a-z0-9]+)*$", var.project_name))
    error_message = "project_name must be a lowercase ECR-compatible name."
  }
}

variable "environment" {
  description = "Environment tag applied to Day 17 ECR resources."
  type        = string
  default     = "day17"
}

variable "untagged_expire_days" {
  description = "Number of days to keep untagged images."
  type        = number
  default     = 7

  validation {
    condition     = var.untagged_expire_days >= 1
    error_message = "untagged_expire_days must be at least 1."
  }
}

variable "tagged_image_count" {
  description = "Maximum number of tagged images retained per repository."
  type        = number
  default     = 30

  validation {
    condition     = var.tagged_image_count >= 1
    error_message = "tagged_image_count must be at least 1."
  }
}

variable "common_tags" {
  description = "Additional tags merged with the standard project tags."
  type        = map(string)
  default     = {}
}
