variable "aws_region" {
  description = "AWS region for all application resources."
  type        = string
}

variable "project_name" {
  description = "Name prefix for resources."
  type        = string
  default     = "cost-optimizer"
}

variable "environment" {
  description = "Deployment environment name, used in resource names and the Environment tag."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,15}$", var.environment))
    error_message = "Use 2-16 lowercase letters, digits or hyphens."
  }
}

variable "repository_url" {
  description = "Source repository, recorded as a tag."
  type        = string
  default     = "unspecified"
}

variable "additional_tags" {
  description = "Extra tags for every resource, for example Owner or CostCenter."
  type        = map(string)
  default     = {}
}

variable "allowed_ingress_cidrs" {
  description = <<-EOT
    CIDR blocks allowed to reach the load balancer. There is deliberately no default: the
    application holds cost data, so decide explicitly who can reach it (for example an office
    or VPN range). Use ["0.0.0.0/0"] only together with HTTPS.
  EOT
  type        = list(string)

  validation {
    condition     = length(var.allowed_ingress_cidrs) > 0 && alltrue([for c in var.allowed_ingress_cidrs : can(cidrhost(c, 0))])
    error_message = "Provide at least one valid CIDR block."
  }
}

variable "certificate_arn" {
  description = "ACM certificate ARN for HTTPS. Required unless allow_insecure_http is true."
  type        = string
  default     = ""

  validation {
    condition     = var.certificate_arn != "" || var.allow_insecure_http
    error_message = "Provide certificate_arn, or set allow_insecure_http = true to accept plain HTTP (testing only)."
  }
}

variable "allow_insecure_http" {
  description = "Serve plain HTTP when no certificate is configured. Credentials would cross the network unencrypted; use only for short-lived tests from trusted networks."
  type        = bool
  default     = false
}

variable "vpc_cidr" {
  description = "CIDR block of the VPC (a /16 is split into /20 subnets)."
  type        = string
  default     = "10.40.0.0/16"
}

variable "enable_nat_gateway" {
  description = <<-EOT
    Run tasks in private subnets behind a single NAT gateway (about USD 33/month plus data
    processing in us-east-1). When false, tasks run in public subnets with public IPs but still
    accept traffic only from the load balancer's security group.
  EOT
  type        = bool
  default     = true
}

variable "image_tag" {
  description = "Image tag used for the initial task definition. Later deployments are made by the deploy workflow."
  type        = string
  default     = "latest"
}

variable "task_cpu" {
  description = "Fargate task CPU units."
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Fargate task memory (MiB)."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Number of running tasks."
  type        = number
  default     = 1
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Initial RDS storage in GiB (gp3)."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Upper limit for RDS storage autoscaling in GiB."
  type        = number
  default     = 50
}

variable "db_multi_az" {
  description = "Run RDS in Multi-AZ mode (roughly doubles the database cost)."
  type        = bool
  default     = false
}

variable "db_backup_retention_days" {
  description = "Automated backup retention for RDS."
  type        = number
  default     = 7
}

variable "deletion_protection" {
  description = "Protect the database and load balancer from deletion. Disable explicitly before destroying."
  type        = bool
  default     = true
}

variable "admin_username" {
  description = "Username of the application's single operator account (the password lives in Secrets Manager)."
  type        = string
  default     = "admin"
}

variable "enable_cost_explorer" {
  description = "Enable the Cost Explorer integration and grant the task role ce:GetCostAndUsage. Each API request costs USD 0.01."
  type        = bool
  default     = false
}

variable "cost_allocation_tag_key" {
  description = "Cost allocation tag used by the application to group costs by environment."
  type        = string
  default     = "Environment"
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for application logs."
  type        = number
  default     = 30
}

variable "alarm_sns_topic_arn" {
  description = "SNS topic notified by CloudWatch alarms. Empty creates alarms without notifications."
  type        = string
  default     = ""
}
