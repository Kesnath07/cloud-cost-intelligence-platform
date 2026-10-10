locals {
  container_name = "app"
  task_subnets   = var.enable_nat_gateway ? aws_subnet.private_app[*].id : aws_subnet.public[*].id
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name}"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    # Container Insights adds CloudWatch charges; basic ECS metrics are sufficient here.
    name  = "containerInsights"
    value = "disabled"
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = local.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = local.container_name
    image     = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
    essential = true
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    environment = [
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${aws_db_instance.main.db_name}?sslmode=require" },
      { name = "DB_USERNAME", value = aws_db_instance.main.username },
      { name = "APP_ADMIN_USERNAME", value = var.admin_username },
      { name = "COSTPLATFORM_AWS_ENABLED", value = tostring(var.enable_cost_explorer) },
      { name = "COSTPLATFORM_AWS_ENVIRONMENT_TAG_KEY", value = var.cost_allocation_tag_key },
    ]
    secrets = [
      { name = "DB_PASSWORD", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::" },
      { name = "APP_ADMIN_PASSWORD", valueFrom = aws_secretsmanager_secret.admin_password.arn },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "app"
      }
    }
  }])
}

resource "aws_ecs_service" "app" {
  name                              = local.name
  cluster                           = aws_ecs_cluster.main.id
  task_definition                   = aws_ecs_task_definition.app.arn
  desired_count                     = var.desired_count
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 120
  propagate_tags                    = "SERVICE"

  network_configuration {
    subnets          = local.task_subnets
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = !var.enable_nat_gateway
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = local.container_name
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # The deploy workflow registers new task definition revisions with new image tags.
  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener.http]
}
