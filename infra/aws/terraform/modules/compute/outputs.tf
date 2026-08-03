output "instance_id" {
  value = aws_instance.this.id
}

output "public_ip" {
  value = aws_instance.this.public_ip
}

output "public_dns" {
  value = aws_instance.this.public_dns
}

output "iam_role_name" {
  value = aws_iam_role.this.name
}

output "iam_instance_profile_name" {
  value = aws_iam_instance_profile.this.name
}

output "private_dns" {
  value = aws_instance.this.private_dns
}

output "private_ip" {
  value = aws_instance.this.private_ip
}
