# Production-like settings. allowed_ingress_cidrs and certificate_arn are intentionally not
# committed; pass them with -var or a git-ignored *.auto.tfvars file.
aws_region         = "us-east-1"
environment        = "prod"
enable_nat_gateway = true
desired_count      = 1
db_instance_class  = "db.t4g.micro"
db_multi_az        = false
log_retention_days = 30
additional_tags = {
  Owner = "platform-team"
}
