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

variable "runtime_instance_type" {
  type    = string
  default = "t3.medium"
}

variable "application_instance_type" {
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

variable "hosted_zone_name" {
  description = "Existing public Route 53 hosted zone used for the application record."
  type        = string
  default     = "fin-risk.com"
}

variable "application_domain_name" {
  description = "Public HTTPS hostname for FinRisk Radar."
  type        = string
  default     = "app.fin-risk.com"
}

variable "application_fleet_enabled" {
  description = "Enable only after the current Day 19 release parameter has been initialized."
  type        = bool
  default     = false
}

variable "application_min_size" {
  type    = number
  default = 1
}

variable "application_desired_capacity" {
  type    = number
  default = 2
}

variable "application_max_size" {
  type    = number
  default = 3
}

variable "legacy_direct_http_enabled" {
  description = "Keep the original EC2 HTTP endpoint during the staged Day 19 cutover."
  type        = bool
  default     = true
}
