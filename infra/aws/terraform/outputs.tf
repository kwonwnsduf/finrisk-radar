output "repository_urls" {
  description = "ECR repository URLs keyed by application image name."
  value = {
    for name, repository in aws_ecr_repository.application :
    name => repository.repository_url
  }
}

output "backend_repository_url" {
  description = "ECR URL for the backend image."
  value       = aws_ecr_repository.application["backend"].repository_url
}

output "frontend_repository_url" {
  description = "ECR URL for the frontend image."
  value       = aws_ecr_repository.application["frontend"].repository_url
}

output "repository_names" {
  description = "ECR repository names keyed by application image name."
  value = {
    for name, repository in aws_ecr_repository.application :
    name => repository.name
  }
}
