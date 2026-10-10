variable "aws_region" {
  description = "Region for the state bucket and IAM resources' regional ARNs."
  type        = string
}

variable "project_name" {
  description = "Name prefix used by the application stack (must match ../app)."
  type        = string
  default     = "cost-optimizer"
}

variable "app_environment" {
  description = "Application environment the deploy role may update (must match ../app's environment)."
  type        = string
  default     = "prod"
}

variable "state_bucket_name" {
  description = "Globally unique name for the Terraform state bucket."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the deploy role, as owner/name."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "Use the form owner/repository."
  }
}

variable "github_environment" {
  description = "GitHub Actions environment whose jobs may assume the deploy role."
  type        = string
  default     = "production"
}

variable "create_github_oidc_provider" {
  description = "Create the GitHub OIDC identity provider. Set to false if the account already has one."
  type        = bool
  default     = true
}
