data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_iam_policy_document" "assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "this" {
  name               = "${var.name_prefix}-ec2"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "runtime" {
  statement {
    sid       = "EcrAuthorization"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PullApplicationImages"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer"
    ]
    resources = var.ecr_repository_arns
  }

  statement {
    sid     = "WriteApplicationRawData"
    actions = ["s3:PutObject"]
    resources = [
      "${var.application_bucket_arn}/market-prices/*",
      "${var.application_bucket_arn}/financial-statements/*",
      "${var.application_bucket_arn}/dart-corp-codes/*",
      "${var.application_bucket_arn}/debt-maturity/*",
      "${var.application_bucket_arn}/documents/raw/*"
    ]
  }

  statement {
    sid = "ReadDay18Secrets"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters"
    ]
    resources = values(var.secret_parameter_arns)
  }

  statement {
    sid = "WriteApplicationLogs"
    actions = [
      "logs:CreateLogStream",
      "logs:DescribeLogStreams",
      "logs:PutLogEvents"
    ]
    resources = [
      "${var.container_log_group_arn}:*",
      "${var.bootstrap_log_group_arn}:*"
    ]
  }

  statement {
    sid       = "DiscoverPrecreatedLogGroups"
    actions   = ["logs:DescribeLogGroups"]
    resources = ["*"]
  }

  dynamic "statement" {
    for_each = var.application_kms_key_arn == null ? [] : [var.application_kms_key_arn]
    content {
      sid       = "EncryptApplicationObjects"
      actions   = ["kms:Encrypt", "kms:GenerateDataKey"]
      resources = [statement.value]
    }
  }
}

resource "aws_iam_role_policy" "runtime" {
  name   = "${var.name_prefix}-runtime"
  role   = aws_iam_role.this.id
  policy = data.aws_iam_policy_document.runtime.json
}

resource "aws_iam_instance_profile" "this" {
  name = "${var.name_prefix}-ec2"
  role = aws_iam_role.this.name
}

locals {
  compose = templatefile("${path.module}/templates/docker-compose.aws.yml.tftpl", {
    aws_region               = var.aws_region
    backend_image            = var.backend_image
    frontend_image           = var.frontend_image
    container_log_group_name = var.container_log_group_name
  })
  nginx = file("${path.module}/templates/nginx.conf")
  cloudwatch_agent = templatefile("${path.module}/templates/cloudwatch-agent.json.tftpl", {
    bootstrap_log_group_name = var.bootstrap_log_group_name
  })
  deploy_script = templatefile("${path.module}/templates/deploy.sh.tftpl", {
    application_bucket_name = var.application_bucket_name
    aws_region              = var.aws_region
    db_address              = var.db_address
    db_name                 = var.db_name
    db_username             = var.db_username
    ecr_registry            = var.ecr_registry
    google_client_id        = var.google_client_id
    naver_client_id         = var.naver_client_id
    openai_llm_model        = var.openai_llm_model
    parameter_dart          = var.secret_parameter_names["dart_api_key"]
    parameter_db            = var.secret_parameter_names["postgres_password"]
    parameter_google        = var.secret_parameter_names["google_client_secret"]
    parameter_jwt           = var.secret_parameter_names["jwt_secret"]
    parameter_naver         = var.secret_parameter_names["naver_client_secret"]
    parameter_openai        = var.secret_parameter_names["openai_api_key"]
    parameter_redis         = var.secret_parameter_names["redis_password"]
    parameter_toss          = var.secret_parameter_names["toss_widget_secret_key"]
    toss_widget_client_key  = var.toss_widget_client_key
  })
  user_data = templatefile("${path.module}/templates/cloud-init.yaml.tftpl", {
    aws_region             = var.aws_region
    cloudwatch_agent_b64   = base64encode(local.cloudwatch_agent)
    compose_b64            = base64encode(local.compose)
    deploy_script_b64      = base64encode(local.deploy_script)
    docker_compose_version = var.docker_compose_version
    nginx_b64              = base64encode(local.nginx)
  })
}

resource "aws_instance" "this" {
  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = aws_iam_instance_profile.this.name

  associate_public_ip_address = true
  # EC2 limits raw user data to 16 KiB. Cloud-init transparently expands
  # gzip-compressed payloads, which keeps the embedded deployment assets
  # below that limit without fetching configuration from a public location.
  user_data_base64            = base64gzip(local.user_data)
  user_data_replace_on_change = true

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    encrypted             = true
    delete_on_termination = true
  }

  tags = { Name = "${var.name_prefix}-app" }

  depends_on = [
    aws_iam_role_policy.runtime,
    aws_iam_role_policy_attachment.ssm_core
  ]
}
