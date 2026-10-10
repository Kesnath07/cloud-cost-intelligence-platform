output "load_balancer_dns_name" {
  description = "Public DNS name of the load balancer."
  value       = aws_lb.main.dns_name
}

output "ecr_repository_url" {
  description = "Push application images here."
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "ecs_task_family" {
  value = aws_ecs_task_definition.app.family
}

output "container_name" {
  value = local.container_name
}

output "admin_password_secret_arn" {
  description = "Set the operator password in this secret before the first deployment."
  value       = aws_secretsmanager_secret.admin_password.arn
}

output "database_endpoint" {
  description = "Private database endpoint (reachable only from the application tasks)."
  value       = aws_db_instance.main.address
}
