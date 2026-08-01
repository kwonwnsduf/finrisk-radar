output "db_address" {
  value = aws_db_instance.this.address
}

output "db_port" {
  value = aws_db_instance.this.port
}

output "container_log_group_name" {
  value = aws_cloudwatch_log_group.containers.name
}

output "container_log_group_arn" {
  value = aws_cloudwatch_log_group.containers.arn
}

output "bootstrap_log_group_name" {
  value = aws_cloudwatch_log_group.bootstrap.name
}

output "bootstrap_log_group_arn" {
  value = aws_cloudwatch_log_group.bootstrap.arn
}
