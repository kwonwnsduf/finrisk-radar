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

variable "instance_id" {
  type = string
}
