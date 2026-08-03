output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

output "alb_arn_suffix" {
  value = aws_lb.this.arn_suffix
}

output "target_group_arn" {
  value = aws_lb_target_group.application.arn
}

output "target_group_arn_suffix" {
  value = aws_lb_target_group.application.arn_suffix
}

output "application_url" {
  value = "https://${var.domain_name}"
}
