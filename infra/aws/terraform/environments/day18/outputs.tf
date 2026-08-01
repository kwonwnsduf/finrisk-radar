output "public_url" {
  value = "http://${module.compute.public_ip}"
}

output "instance_id" {
  value = module.compute.instance_id
}

output "rds_endpoint" {
  value = module.data.db_address
}

output "application_bucket" {
  value = data.aws_s3_bucket.application.id
}

output "backend_image" {
  value = local.backend_image
}

output "frontend_image" {
  value = local.frontend_image
}

output "secret_parameter_names" {
  value = local.secret_parameter_names
}

output "github_actions_role_arn" {
  value = module.cicd.role_arn
}
