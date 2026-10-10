# Lower-cost development settings: no NAT gateway (tasks get public IPs but only accept
# traffic from the load balancer), shorter log retention.
aws_region               = "us-east-1"
environment              = "dev"
enable_nat_gateway       = false
desired_count            = 1
db_instance_class        = "db.t4g.micro"
db_backup_retention_days = 1
log_retention_days       = 7
# Short-lived test environments without a certificate must opt in to plain HTTP explicitly.
allow_insecure_http = true
additional_tags = {
  Owner = "platform-team"
}
