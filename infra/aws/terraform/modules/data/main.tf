resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db"
  subnet_ids = var.private_subnet_ids

  tags = { Name = "${var.name_prefix}-db" }
}

resource "aws_db_parameter_group" "this" {
  name   = "${var.name_prefix}-postgres17"
  family = "postgres17"

  tags = { Name = "${var.name_prefix}-postgres17" }
}

resource "aws_db_instance" "this" {
  identifier = "${var.name_prefix}-postgres"

  engine         = "postgres"
  engine_version = "17"
  instance_class = var.db_instance_class

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username

  password_wo         = var.db_password
  password_wo_version = var.db_password_version

  db_subnet_group_name   = aws_db_subnet_group.this.name
  parameter_group_name   = aws_db_parameter_group.this.name
  vpc_security_group_ids = [var.rds_security_group_id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period         = 1
  performance_insights_enabled    = false
  monitoring_interval             = 0
  auto_minor_version_upgrade      = true
  apply_immediately               = true
  deletion_protection             = false
  skip_final_snapshot             = true
  delete_automated_backups        = true
  enabled_cloudwatch_logs_exports = []
  copy_tags_to_snapshot           = true

  tags = { Name = "${var.name_prefix}-postgres" }
}

resource "aws_cloudwatch_log_group" "containers" {
  name              = "/finrisk/day18/containers"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_log_group" "bootstrap" {
  name              = "/finrisk/day18/bootstrap"
  retention_in_days = var.log_retention_days
}
