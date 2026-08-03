data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_launch_template" "this" {
  name_prefix   = "${var.name_prefix}-application-"
  image_id      = data.aws_ssm_parameter.al2023_ami.value
  instance_type = var.instance_type

  iam_instance_profile {
    name = var.iam_instance_profile_name
  }

  network_interfaces {
    associate_public_ip_address = true
    delete_on_termination       = true
    security_groups             = [var.security_group_id]
  }

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    instance_metadata_tags      = "enabled"
    http_put_response_hop_limit = 2
  }

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      delete_on_termination = true
      encrypted             = true
      volume_size           = var.root_volume_size
      volume_type           = "gp3"
    }
  }

  user_data = base64encode(templatefile("${path.module}/files/bootstrap.sh.tftpl", {
    aws_region                     = var.aws_region
    current_release_parameter_name = var.current_release_parameter_name
  }))

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name                     = "${var.name_prefix}-application"
      FinriskDeployment        = "day19"
      FinriskRole              = "application"
      FinriskDbAddress         = var.db_address
      FinriskRuntimeHost       = var.runtime_private_dns
      FinriskApplicationBucket = var.application_bucket_name
      FinriskGoogleClientId    = var.google_client_id
      FinriskTossClientKey     = var.toss_widget_client_key
      FinriskNaverClientId     = var.naver_client_id
      FinriskOpenAiModel       = var.openai_llm_model
      FinriskContainerLogGroup = var.container_log_group_name
      FinriskPublicBaseUrl     = var.public_base_url
    }
  }

  tag_specifications {
    resource_type = "volume"
    tags = {
      Name              = "${var.name_prefix}-application"
      FinriskDeployment = "day19"
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_autoscaling_group" "this" {
  name                = "${var.name_prefix}-application"
  vpc_zone_identifier = var.subnet_ids
  target_group_arns   = [var.target_group_arn]

  min_size         = var.enabled ? var.min_size : 0
  desired_capacity = var.enabled ? var.desired_capacity : 0
  max_size         = var.max_size

  health_check_type         = "ELB"
  health_check_grace_period = 600
  default_instance_warmup   = 300

  launch_template {
    id      = aws_launch_template.this.id
    version = "$Latest"
  }

  instance_maintenance_policy {
    min_healthy_percentage = 100
    max_healthy_percentage = 150
  }

  enabled_metrics = [
    "GroupDesiredCapacity",
    "GroupInServiceInstances",
    "GroupMinSize",
    "GroupMaxSize"
  ]

  wait_for_capacity_timeout = "20m"

  tag {
    key                 = "Name"
    value               = "${var.name_prefix}-application"
    propagate_at_launch = true
  }

  tag {
    key                 = "FinriskDeployment"
    value               = "day19"
    propagate_at_launch = true
  }

  tag {
    key                 = "FinriskRole"
    value               = "application"
    propagate_at_launch = true
  }
}

resource "aws_cloudwatch_metric_alarm" "in_service_capacity" {
  alarm_name          = "${var.name_prefix}-application-capacity"
  alarm_description   = "The application fleet has fewer than two InService instances."
  namespace           = "AWS/AutoScaling"
  metric_name         = "GroupInServiceInstances"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 2
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"

  dimensions = {
    AutoScalingGroupName = aws_autoscaling_group.this.name
  }
}
