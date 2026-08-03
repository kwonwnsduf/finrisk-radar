locals {
  name_prefix      = "${var.project_name}-${var.environment}"
  parameter_prefix = "/finrisk/day18"
  public_base_url  = "https://${var.application_domain_name}"
  secret_parameter_names = {
    postgres_password      = "${local.parameter_prefix}/postgres/password"
    redis_password         = "${local.parameter_prefix}/redis/password"
    jwt_secret             = "${local.parameter_prefix}/jwt/secret"
    google_client_secret   = "${local.parameter_prefix}/google/client-secret"
    toss_widget_secret_key = "${local.parameter_prefix}/toss/widget-secret-key"
    dart_api_key           = "${local.parameter_prefix}/dart/api-key"
    naver_client_secret    = "${local.parameter_prefix}/naver/client-secret"
    openai_api_key         = "${local.parameter_prefix}/openai/api-key"
  }
  secret_parameter_arns = {
    for key, name in local.secret_parameter_names :
    key => format(
      "arn:aws:ssm:%s:%s:parameter%s",
      var.aws_region,
      data.aws_caller_identity.current.account_id,
      name
    )
  }
  availability_zones = slice(data.aws_availability_zones.available.names, 0, 2)
}

resource "aws_ssm_parameter" "release_current" {
  name        = "/finrisk/day19/releases/current"
  description = "Last fully approved Day 19 release manifest."
  type        = "String"
  value       = "UNSET"

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "release_previous" {
  name        = "/finrisk/day19/releases/previous"
  description = "Previous fully approved Day 19 release manifest."
  type        = "String"
  value       = "UNSET"

  lifecycle {
    ignore_changes = [value]
  }
}

ephemeral "aws_ssm_parameter" "db_password" {
  arn             = local.secret_parameter_arns["postgres_password"]
  with_decryption = true

  lifecycle {
    postcondition {
      condition     = self.type == "SecureString"
      error_message = "The PostgreSQL password must be stored as an SSM SecureString."
    }
  }
}

module "network" {
  source = "../../modules/network"

  name_prefix                = local.name_prefix
  vpc_cidr                   = "10.18.0.0/16"
  availability_zones         = local.availability_zones
  public_subnet_cidrs        = ["10.18.0.0/24", "10.18.1.0/24"]
  private_subnet_cidrs       = ["10.18.10.0/24", "10.18.11.0/24"]
  allowed_http_cidrs         = var.allowed_http_cidrs
  legacy_direct_http_enabled = var.legacy_direct_http_enabled
}

module "data" {
  source = "../../modules/data"

  name_prefix           = local.name_prefix
  private_subnet_ids    = module.network.private_subnet_ids
  rds_security_group_id = module.network.rds_security_group_id
  db_name               = "finrisk"
  db_username           = "finrisk"
  db_password           = ephemeral.aws_ssm_parameter.db_password.value
  db_password_version   = var.db_password_version
  db_instance_class     = var.db_instance_class
  log_retention_days    = 7
}

module "compute" {
  source = "../../modules/compute"

  name_prefix       = local.name_prefix
  aws_region        = var.aws_region
  subnet_id         = module.network.public_subnet_ids[0]
  security_group_id = module.network.ec2_security_group_id
  instance_type     = var.instance_type
  root_volume_size  = 20

  ecr_repository_arns = [
    data.aws_ecr_repository.backend.arn,
    data.aws_ecr_repository.frontend.arn
  ]
  db_address = module.data.db_address

  application_bucket_name = data.aws_s3_bucket.application.id
  application_bucket_arn  = data.aws_s3_bucket.application.arn
  application_kms_key_arn = var.application_kms_key_arn

  secret_parameter_arns = local.secret_parameter_arns

  container_log_group_name = module.data.container_log_group_name
  container_log_group_arn  = module.data.container_log_group_arn
  bootstrap_log_group_arn  = module.data.bootstrap_log_group_arn

  google_client_id       = var.google_client_id
  toss_widget_client_key = var.toss_widget_client_key
  naver_client_id        = var.naver_client_id
  openai_llm_model       = var.openai_llm_model
  public_base_url        = local.public_base_url
  release_parameter_arns = [
    aws_ssm_parameter.release_current.arn,
    aws_ssm_parameter.release_previous.arn
  ]

  depends_on = [module.data]
}

module "load_balancing" {
  source = "../../modules/load_balancing"

  name_prefix       = local.name_prefix
  vpc_id            = module.network.vpc_id
  public_subnet_ids = module.network.public_subnet_ids
  security_group_id = module.network.alb_security_group_id
  hosted_zone_id    = data.aws_route53_zone.application.zone_id
  domain_name       = var.application_domain_name
}

module "application_fleet" {
  source = "../../modules/application_fleet"

  name_prefix                    = local.name_prefix
  aws_region                     = var.aws_region
  subnet_ids                     = module.network.public_subnet_ids
  security_group_id              = module.network.application_security_group_id
  iam_instance_profile_name      = module.compute.iam_instance_profile_name
  target_group_arn               = module.load_balancing.target_group_arn
  instance_type                  = var.instance_type
  root_volume_size               = 20
  enabled                        = var.application_fleet_enabled
  min_size                       = var.application_min_size
  desired_capacity               = var.application_desired_capacity
  max_size                       = var.application_max_size
  current_release_parameter_name = aws_ssm_parameter.release_current.name
  runtime_private_dns            = module.compute.private_dns
  db_address                     = module.data.db_address
  application_bucket_name        = data.aws_s3_bucket.application.id
  google_client_id               = var.google_client_id
  toss_widget_client_key         = var.toss_widget_client_key
  naver_client_id                = var.naver_client_id
  openai_llm_model               = var.openai_llm_model
  container_log_group_name       = module.data.container_log_group_name
  public_base_url                = local.public_base_url
}

module "cicd" {
  source = "../../modules/cicd"

  name_prefix       = local.name_prefix
  aws_region        = var.aws_region
  aws_account_id    = data.aws_caller_identity.current.account_id
  github_repository = var.github_repository
  github_branch     = var.github_branch
  ecr_repository_arns = [
    data.aws_ecr_repository.backend.arn,
    data.aws_ecr_repository.frontend.arn
  ]
  runtime_instance_id    = module.compute.instance_id
  application_asg_name   = module.application_fleet.autoscaling_group_name
  application_bucket_arn = data.aws_s3_bucket.application.arn
  release_parameter_arns = [
    aws_ssm_parameter.release_current.arn,
    aws_ssm_parameter.release_previous.arn
  ]
}
