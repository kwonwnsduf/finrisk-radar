variable "name_prefix" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}

variable "root_volume_size" {
  type    = number
  default = 20
}

variable "ecr_repository_arns" {
  type = list(string)
}

variable "db_address" {
  type = string
}

variable "application_bucket_name" {
  type = string
}

variable "application_bucket_arn" {
  type = string
}

variable "application_kms_key_arn" {
  type    = string
  default = null
}

variable "secret_parameter_arns" {
  type = map(string)
}

variable "container_log_group_name" {
  type = string
}

variable "container_log_group_arn" {
  type = string
}

variable "bootstrap_log_group_arn" {
  type = string
}

variable "google_client_id" {
  type = string
}

variable "toss_widget_client_key" {
  type = string
}

variable "naver_client_id" {
  type = string
}

variable "openai_llm_model" {
  type = string
}
