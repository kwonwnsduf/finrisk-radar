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

output "secret_parameter_names" {
  value = local.secret_parameter_names
}

output "github_actions_role_arn" {
  value = module.cicd.role_arn
}

output "application_url" {
  value = module.load_balancing.application_url
}

output "alb_dns_name" {
  value = module.load_balancing.alb_dns_name
}

output "target_group_arn" {
  value = module.load_balancing.target_group_arn
}

output "application_asg_name" {
  value = module.application_fleet.autoscaling_group_name
}

output "runtime_instance_id" {
  value = module.compute.instance_id
}
