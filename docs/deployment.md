# Deployment

> **AWS resources created by this configuration incur charges** (see the estimate below). Nothing
> was applied or deployed while developing this repository; review every plan before applying it.

Infrastructure lives in `infra/terraform`:

| Directory | Purpose | State |
| --- | --- | --- |
| `bootstrap/` | One-time setup: Terraform state bucket, GitHub OIDC provider, GitHub deploy role | Local (`terraform.tfstate`, git-ignored; keep a copy) |
| `app/` | VPC, load balancer, ECS Fargate, ECR, RDS PostgreSQL, IAM, secrets, logs and alarms | Remote S3 backend created by `bootstrap/` |

Environments are separated by tfvars files (`app/environments/dev.tfvars`, `prod.tfvars`) and by
the state key in `backend.hcl`. No modules are used: one small application stack does not need them.

## 1. Bootstrap (once per AWS account)

Requires administrator credentials on your workstation.

```bash
cd infra/terraform/bootstrap
cp terraform.tfvars.example terraform.tfvars   # set region, a unique bucket name, owner/repo
terraform init
terraform plan -out bootstrap.tfplan
terraform apply bootstrap.tfplan
```

Outputs: `state_bucket_name` and `github_deploy_role_arn`. Set `create_github_oidc_provider = false`
if the account already has the GitHub OIDC provider. The state bucket has `prevent_destroy`.
State locking uses S3 native lock files (`use_lockfile`), so no DynamoDB table is needed.

## 2. Application stack

```bash
cd infra/terraform/app
cp backend.hcl.example backend.hcl            # bucket from step 1, a key per environment
terraform init -backend-config=backend.hcl

cat > prod.auto.tfvars <<'TFVARS'             # git-ignored; not secrets, but environment-specific
allowed_ingress_cidrs = ["203.0.113.0/24"]    # your office/VPN range
certificate_arn       = "arn:aws:acm:us-east-1:<account>:certificate/<id>"
repository_url        = "https://github.com/<owner>/cloud-cost-optimization-platform"
TFVARS

terraform plan -var-file=environments/prod.tfvars -out prod.tfplan
terraform apply prod.tfplan
```

Required decisions without defaults: `allowed_ingress_cidrs`, and either `certificate_arn` or
`allow_insecure_http = true` (testing only). Defaults are non-destructive: deletion protection on
the database and load balancer, final database snapshot, 7-day secret recovery window, immutable
image tags.

The first apply creates the ECS service with image tag `latest`, which does not exist yet; the
service cannot start tasks until the first deployment pushes an image (step 4). Alternatively
build and push an image manually and pass `-var image_tag=<tag>`.

## 3. Secrets

The database master password is created and stored by RDS automatically. Set the operator password
once (it is never in Terraform):

```bash
aws secretsmanager put-secret-value \
  --secret-id "$(terraform output -raw admin_password_secret_arn)" \
  --secret-string "$(openssl rand -base64 24)"
```

Retrieve it later with `aws secretsmanager get-secret-value`. Tasks fail to start until it is set.

## 4. GitHub Actions deployment (OIDC)

1. In the GitHub repository create an **environment** named `production` and add required
   reviewers.
2. Add these environment **variables** (none of them are secrets):

   | Variable | Value |
   | --- | --- |
   | `AWS_REGION` | e.g. `us-east-1` |
   | `AWS_DEPLOY_ROLE_ARN` | `github_deploy_role_arn` from bootstrap |
   | `ECR_REPOSITORY` | repository name, e.g. `cost-optimizer-prod` |
   | `ECS_CLUSTER` | `terraform output -raw ecs_cluster_name` |
   | `ECS_SERVICE` | `terraform output -raw ecs_service_name` |
   | `ECS_TASK_FAMILY` | `terraform output -raw ecs_task_family` |
   | `CONTAINER_NAME` | `terraform output -raw container_name` |

3. Run **Actions → Deploy to AWS → Run workflow**. The job runs the test suite, builds the image,
   pushes it tagged with the commit SHA, renders the latest task definition revision with the new
   image and waits for the ECS service to become stable. Failed deployments roll back through the
   ECS deployment circuit breaker.

The deploy role can only push to this application's ECR repository, register task definitions,
update this one ECS service and pass its two task roles. Terraform ignores the service's task
definition so deployments and infrastructure changes do not fight; settings changed in Terraform
(environment variables, CPU, memory) are picked up by the next deployment, which starts from the
latest registered revision.

`ci.yml` and `terraform.yml` run on every push and pull request without any AWS access.

## 5. Cost Explorer from the deployed application

Set `enable_cost_explorer = true`, apply, and redeploy. The task role then receives
`ce:GetCostAndUsage`, and the application uses it through the default credential provider chain.
Activate the `Environment` cost allocation tag in the Billing console; the Terraform default tags
put it on every resource of this stack, so the platform can analyze its own cost.

## Estimated monthly cost (us-east-1, on-demand, approximate)

| Resource | prod.tfvars | dev.tfvars |
| --- | --- | --- |
| Application Load Balancer (hours + minimal LCU) | ~ USD 18 | ~ USD 18 |
| ECS Fargate, 0.5 vCPU / 1 GB, 1 task, x86 | ~ USD 18 | ~ USD 18 |
| RDS `db.t4g.micro` single-AZ + 20 GB gp3 + backups | ~ USD 15 | ~ USD 14 |
| NAT gateway (hours, excluding data processing) | ~ USD 33 | — (disabled) |
| Public IPv4 addresses (load balancer, NAT, or task) | ~ USD 11 | ~ USD 11 |
| Secrets Manager (2 secrets), CloudWatch logs and alarms, ECR storage | ~ USD 3 | ~ USD 3 |
| **Total** | **~ USD 95–105** | **~ USD 60–70** |

These are rough figures from public on-demand prices at the time of writing and exclude data
transfer, NAT data processing (USD 0.045/GB), Cost Explorer API requests (USD 0.01 each), taxes
and free-tier effects. Prices change; use the AWS Pricing Calculator for your region and usage.
Multi-AZ RDS roughly doubles the database line.

## Tearing down

Deletion protection is on by default. To remove an environment intentionally:

```bash
terraform apply -var-file=environments/prod.tfvars -var deletion_protection=false
terraform destroy -var-file=environments/prod.tfvars
```

RDS keeps a final snapshot (`<name>-final`), which continues to incur storage charges until
deleted. Delete images in ECR first if the repository is not empty. The bootstrap state bucket is
protected with `prevent_destroy` and should normally be kept.
