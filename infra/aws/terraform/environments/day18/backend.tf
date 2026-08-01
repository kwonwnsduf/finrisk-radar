terraform {
  backend "s3" {
    key          = "finrisk-radar/day18/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
