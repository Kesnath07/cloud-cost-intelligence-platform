output "state_bucket_name" {
  description = "Use as `bucket` in ../app/backend.hcl."
  value       = aws_s3_bucket.state.bucket
}

output "github_deploy_role_arn" {
  description = "Set as the AWS_DEPLOY_ROLE_ARN variable of the GitHub environment."
  value       = aws_iam_role.github_deploy.arn
}
