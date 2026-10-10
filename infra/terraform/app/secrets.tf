# The secret container is managed here, but its value is set outside Terraform so that it never
# appears in variables, plans or state:
#   aws secretsmanager put-secret-value --secret-id <admin_password_secret_arn> --secret-string '<password>'
# Tasks fail to start until a value exists.
resource "aws_secretsmanager_secret" "admin_password" {
  name                    = "${local.name}/app-admin-password"
  description             = "Password of the Cloud Cost Optimization Platform operator account"
  recovery_window_in_days = 7
}
