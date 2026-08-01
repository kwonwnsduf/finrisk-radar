variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}

variable "project_name" {
  type    = string
  default = "finrisk"
}

variable "environment" {
  type    = string
  default = "day18"
}

variable "backend_repository_name" {
  type    = string
  default = "finrisk-backend"
}

variable "frontend_repository_name" {
  type    = string
  default = "finrisk-frontend"
}

variable "application_bucket_name" {
  type    = string
  default = "finrisk-raw-data"
}

variable "application_kms_key_arn" {
  description = "Set only when the existing application bucket uses a customer-managed KMS key."
  type        = string
  default     = null
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}

variable "db_instance_class" {
  type    = string
  default = "db.t3.micro"
}

variable "db_password_version" {
  description = "Increment after rotating /finrisk/day18/postgres/password."
  type        = number
  default     = 1
}

variable "allowed_http_cidrs" {
  type    = list(string)
  default = ["0.0.0.0/0"]
}

variable "google_client_id" {
  description = "Non-secret Google OAuth client identifier."
  type        = string
}

variable "toss_widget_client_key" {
  description = "Non-secret Toss widget client key embedded in the frontend image."
  type        = string
}

variable "naver_client_id" {
  description = "Naver API client identifier."
  type        = string
}

variable "openai_llm_model" {
  description = "OpenAI Responses API model used for report generation."
  type        = string
}

variable "common_tags" {
  type    = map(string)
  default = {}
}

variable "github_repository" {
  description = "Repository allowed to deploy through GitHub Actions OIDC."
  type        = string
  default     = "kwonwnsduf/finrisk-radar"
}

variable "github_branch" {
  description = "Only this branch may assume the Day18 deployment role."
  type        = string
  default     = "main"
}
