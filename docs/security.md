# Security

## Application

| Area | Implementation |
| --- | --- |
| Authentication | One operator account (`APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`). The UI uses form login with a session; `/api/**` uses stateless HTTP Basic. Without `APP_ADMIN_PASSWORD`, Spring Boot generates a random password at startup and logs it, which is only acceptable locally. A `{bcrypt}` hash can be supplied instead of a plain password. |
| Authorization | Every page and API endpoint requires authentication except the login page, static assets and `/actuator/health` (needed by the load balancer). There are no roles: this is a single-team tool, not a multi-tenant service. |
| CSRF | Enabled for all UI forms (tokens added by Thymeleaf). Disabled only for the stateless Basic-authenticated API, which uses no cookies. |
| Headers | Strict Content Security Policy (`default-src 'self'`, no inline scripts or styles; charts are server-rendered SVG), `X-Frame-Options: DENY`, `nosniff`, HSTS on HTTPS. |
| Errors | API errors are RFC 9457 problem details with user-safe messages; unexpected exceptions return a generic message and are logged server-side. Stack traces, SQL and binding details are never included in responses. |
| Input validation | Bean Validation on API/form input, strict CSV validation (types, ranges, sizes, row limits, upload size limit), parameterized SQL only, sanitized file names. |
| Output | Thymeleaf escapes all text; CSV exports neutralize spreadsheet formulas. |
| AWS errors | Translated to fixed messages; raw AWS messages (ARNs, account IDs) are not exposed. |

### Limits of the authentication model

A single shared operator account means there is no per-user audit trail. Deploy the application
behind a restricted network path (allowed CIDRs, VPN) and HTTPS, as the Terraform configuration
does. Adding SSO (for example OIDC via Spring Security) would be the next step for wider use.

## Secrets

- No credentials or secrets are committed. `.env` is git-ignored; `.env.example` contains
  placeholders only.
- AWS credentials are never configured in the application or `.env`. The AWS SDK default
  credential provider chain is used (IAM task role on AWS, a profile locally).
- On AWS the database master password is generated and stored by RDS in Secrets Manager
  (`manage_master_user_password`); it never appears in Terraform variables or state. The operator
  password is stored in a Secrets Manager secret whose value is set outside Terraform. ECS injects
  both into the container at start-up.
- GitHub Actions authenticates to AWS with OIDC. The deploy role trusts only jobs of this
  repository running in the protected `production` environment. No AWS keys are stored in GitHub.

## Least privilege (AWS)

| Identity | Permissions |
| --- | --- |
| ECS task role (the application) | `ce:GetCostAndUsage` only, and only when `enable_cost_explorer = true` |
| ECS execution role | Managed `AmazonECSTaskExecutionRolePolicy` (ECR pull, logs) plus `secretsmanager:GetSecretValue` on the two application secrets |
| GitHub deploy role | Push to the application's ECR repository, register task definitions, update the one ECS service, pass the two task roles to ECS |

The platform only **reads** cost data. It never creates, modifies, stops or deletes AWS resources;
recommendations are suggestions for people to act on.

## Network (AWS)

- Load balancer reachable only from `allowed_ingress_cidrs` (no default); HTTPS required unless
  `allow_insecure_http` is explicitly set.
- Tasks accept traffic only from the load balancer's security group.
- The database lives in subnets without an internet route, accepts connections only from the task
  security group, is encrypted at rest and enforces TLS (`rds.force_ssl = 1`).
- Task egress is HTTPS (AWS APIs) and PostgreSQL to the database only.

## Supply chain and CI

- GitHub Actions are pinned to commit SHAs; workflows default to `contents: read`; only the deploy
  job receives `id-token: write`. Dependabot keeps actions, Maven, Docker and Terraform providers
  up to date.
- The container runs as a non-root user (UID 10001) on a JRE-only image; ECR scans images on push
  and tags are immutable.
- `trivy config` reports no high or critical findings except three intentional ones, each
  annotated in the Terraform code: the internet-facing load balancer (restricted by CIDR), HTTPS
  egress to AWS APIs (no fixed IP ranges), and SSE-S3 instead of a customer managed key for the
  state bucket (state contains no secrets).
