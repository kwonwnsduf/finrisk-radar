resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

data "aws_iam_policy_document" "assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/${var.github_branch}"]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "${var.name_prefix}-github-actions"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
}

data "aws_iam_policy_document" "deploy" {
  statement {
    sid       = "EcrAuthorization"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "BuildAndPushImages"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart"
    ]
    resources = var.ecr_repository_arns
  }

  statement {
    sid       = "PublishDay19DeploymentArtifacts"
    actions   = ["s3:PutObject"]
    resources = ["${var.application_bucket_arn}/deploy/day19/*"]
  }

  statement {
    sid     = "DeployThroughSsm"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript"
    ]
  }

  statement {
    sid     = "DeployToTaggedDay19Instances"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:aws:ec2:${var.aws_region}:${var.aws_account_id}:instance/*"
    ]

    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/FinriskDeployment"
      values   = ["day19"]
    }
  }

  statement {
    sid       = "DeployToRuntimeInstance"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ec2:${var.aws_region}:${var.aws_account_id}:instance/${var.runtime_instance_id}"]
  }

  statement {
    sid = "ManageDay19StandbyDeployments"
    actions = [
      "autoscaling:EnterStandby",
      "autoscaling:ExitStandby"
    ]
    resources = [
      "arn:aws:autoscaling:${var.aws_region}:${var.aws_account_id}:autoScalingGroup:*:autoScalingGroupName/${var.application_asg_name}"
    ]
  }

  statement {
    sid = "ReadDay19FleetState"
    actions = [
      "autoscaling:DescribeAutoScalingGroups",
      "autoscaling:DescribeAutoScalingInstances",
      "autoscaling:DescribeScalingActivities",
      "elasticloadbalancing:DescribeTargetHealth"
    ]
    resources = ["*"]
  }

  statement {
    sid = "PromoteDay19Release"
    actions = [
      "ssm:GetParameter",
      "ssm:PutParameter"
    ]
    resources = var.release_parameter_arns
  }

  statement {
    sid = "ReadDeploymentResult"
    actions = [
      "ec2:DescribeInstances",
      "ssm:DescribeInstanceInformation",
      "ssm:GetCommandInvocation"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "${var.name_prefix}-github-deploy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.deploy.json
}
