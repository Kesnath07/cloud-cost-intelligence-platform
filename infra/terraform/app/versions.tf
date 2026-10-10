terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Partial configuration: supply bucket, key and region with
  #   terraform init -backend-config=backend.hcl
  # (see backend.hcl.example). The bucket is created by ../bootstrap.
  backend "s3" {}
}

provider "aws" {
  region = var.aws_region

  # Every resource carries these tags. "Environment" is also the cost allocation tag the
  # application groups Cost Explorer data by, so the platform can analyze its own cost.
  default_tags {
    tags = merge({
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
      Repository  = var.repository_url
    }, var.additional_tags)
  }
}
