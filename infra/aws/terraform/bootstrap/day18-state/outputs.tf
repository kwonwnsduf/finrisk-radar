output "state_bucket_name" {
  description = "Bucket to place in environments/day18/backend.hcl."
  value       = aws_s3_bucket.state.id
}

output "backend_config" {
  description = "Non-secret backend configuration for the Day 18 root module."
  value       = <<-EOT
    bucket = "${aws_s3_bucket.state.id}"
  EOT
}
