variable "name_prefix" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "aws_account_id" {
  type = string
}

variable "github_repository" {
  description = "GitHub repository in owner/name form."
  type        = string
}

variable "github_branch" {
  type    = string
  default = "main"
}

variable "ecr_repository_arns" {
  type = list(string)
}

variable "runtime_instance_id" {
  type = string
}

variable "application_asg_name" {
  type = string
}

variable "release_parameter_arns" {
  type = list(string)
}

variable "application_bucket_arn" {
  type = string
}
