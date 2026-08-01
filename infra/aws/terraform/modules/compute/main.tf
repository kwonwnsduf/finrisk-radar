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

resource "aws_instance" "this" {
  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = aws_iam_instance_profile.this.name

  associate_public_ip_address = true
  user_data_base64            = filebase64("${path.module}/files/bootstrap.sh")
  user_data_replace_on_change = true

  metadata_options {
    http_endpoint          = "enabled"
    http_tokens            = "required"
    instance_metadata_tags = "enabled"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name                      = "${var.name_prefix}-app"
    FinriskDbAddress          = var.db_address
    FinriskApplicationBucket = var.application_bucket_name
    FinriskGoogleClientId     = var.google_client_id
    FinriskTossClientKey      = var.toss_widget_client_key
    FinriskNaverClientId      = var.naver_client_id
    FinriskOpenAiModel        = var.openai_llm_model
    FinriskContainerLogGroup  = var.container_log_group_name
  }

  depends_on = [
    aws_iam_role_policy.runtime,
    aws_iam_role_policy_attachment.ssm_core
  ]
}
