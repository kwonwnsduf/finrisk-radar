variable "aws_region" {
  description = "AWS region for the Day 18 Terraform state bucket."
  type        = string
  default     = "ap-northeast-2"
}

variable "state_bucket_name" {
  description = "Optional globally unique bucket name. The account-based default is used when null."
  type        = string
  default     = null
}
