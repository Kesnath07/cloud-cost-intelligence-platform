# AWS Cost Explorer integration

The integration is **optional** and **disabled by default**. With it disabled, no AWS client is
created, no credentials are needed, and the application works entirely from CSV imports or the
demonstration dataset.

## What it does

A synchronization calls the Cost Explorer `GetCostAndUsage` API for a date range (at most 90 days
per run) with:

| Parameter | Value |
| --- | --- |
| Granularity | `DAILY` |
| Metric | `UnblendedCost` by default (`AmortizedCost`, `NetUnblendedCost`, `NetAmortizedCost` or `BlendedCost` are configurable) |
| Group by | `SERVICE` and the `Environment` tag (configurable key) |
| Endpoint region | `us-east-1` (Cost Explorer is served from that region regardless of where the app runs) |

Results are normalized and stored with source `AWS_COST_EXPLORER` and charge type `NET`, because
Cost Explorer returns one amount per group that already nets credits and refunds. The `Estimated`
flag of each day is stored and surfaced as a notice. Tag values that normalize to the same
environment (`Prod`, `prod`) are summed. Pagination is followed up to a configurable page limit.

Because Cost Explorer revises recent figures (estimated data changes until the billing period is
finalized), a synchronization **replaces** all previously synchronized records in its range in a
single transaction. Running the same range again is therefore safe and idempotent. A database
constraint allows only one synchronization to run at a time across all instances; runs left
`RUNNING` by a stopped instance are marked failed after one hour.

An optional schedule (`COSTPLATFORM_AWS_SCHEDULED_SYNC_ENABLED=true`) refreshes the last 7 days
daily at 06:30 UTC.

### What is not broken down

Cost Explorer allows at most two group-by keys. Service and environment are used, so data
synchronized from Cost Explorer has **no account or region breakdown** (those fields stay empty).
Resource-level cost, hourly granularity and resource inventory are not retrieved.

## Prerequisites

1. **Cost Explorer enabled** for the account (Billing and Cost Management console). After it is
   first enabled, data can take up to 24 hours to become available.
2. **Environment tag activated as a cost allocation tag** (Billing console → Cost allocation tags).
   Activation can take up to 24 hours to show in Cost Explorer and applies only to costs incurred
   after activation; earlier costs appear as untagged.
3. In AWS Organizations, the management account controls which member accounts may use Cost
   Explorer and what they can see. A member account sees only its own costs.
4. Credentials with the IAM permission below, available through the **AWS SDK default credential
   provider chain**: environment variables, the shared config/credentials files (`AWS_PROFILE`),
   web identity, the ECS task role, or the EC2 instance profile. The application never reads
   access keys from its own configuration.

## Minimum IAM permissions

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadCostAndUsage",
      "Effect": "Allow",
      "Action": "ce:GetCostAndUsage",
      "Resource": "*"
    }
  ]
}
```

Cost Explorer actions do not support resource-level permissions, so `Resource` must be `*`. The
Terraform task role attaches exactly this policy when `enable_cost_explorer = true`.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `COSTPLATFORM_AWS_ENABLED` | `false` | Turn the integration on |
| `COSTPLATFORM_AWS_ENVIRONMENT_TAG_KEY` | `Environment` | Cost allocation tag used as the environment |
| `COSTPLATFORM_AWS_COST_METRIC` | `UnblendedCost` | Metric to store |
| `COSTPLATFORM_AWS_SCHEDULED_SYNC_ENABLED` | `false` | Daily refresh of recent days |

Further settings (`max-sync-days`, `max-pages`, cron, look-back days) are in `application.yml`
under `costplatform.aws`.

Run a synchronization from **Data Sources → AWS Cost Explorer**, or:

```bash
curl -u admin:$APP_ADMIN_PASSWORD -H 'Content-Type: application/json' \
  -d '{"start":"2026-09-01","end":"2026-09-30"}' http://localhost:8080/api/aws/sync
```

## Pricing and data availability

- **Each paginated `GetCostAndUsage` request is billed** (USD 0.01 per request at the time of
  writing; check the AWS Cost Management pricing page). A 30-day daily request grouped by service
  and tag is typically one or a few pages. The daily schedule therefore costs cents per month, but
  automated loops over long ranges would not. The page cap and the 90-day range limit bound the
  cost of a single run.
- Cost Explorer data is refreshed at least once every 24 hours.
- By default Cost Explorer provides about the last 13 months of history at daily granularity;
  longer history (monthly granularity, up to 38 months) requires opting in to multi-year data in
  the account's Cost Explorer settings. Requests outside the available history fail with an
  explanatory message.

## Error handling

SDK errors are translated into actionable messages, stored on the failed import job and returned
by the API with HTTP 503. Raw AWS error messages are never shown (they can contain ARNs or account
IDs); the AWS request ID is included for correlation with CloudTrail.

| Situation | Message (abridged) |
| --- | --- |
| Integration disabled | Set `COSTPLATFORM_AWS_ENABLED=true` and provide credentials, or import CSV |
| No credentials | Provide them through the default credential provider chain |
| `AccessDeniedException` | Grant `ce:GetCostAndUsage` and enable Cost Explorer |
| Expired or invalid credentials | Refresh them |
| `DataUnavailableException` | No data yet; can take up to 24 hours after enabling |
| `BillExpirationException` | Period older than the available history |
| Throttling / `LimitExceededException` | Wait and retry |
| Network failure | Check access to `ce.us-east-1.amazonaws.com` |

## Testing without AWS

Unit tests mock `CostExplorerClient` (request shape, pagination, tag parsing, currency checks,
error translation) and an integration test runs the full synchronization against PostgreSQL with
a mocked client. No test uses real AWS credentials or makes network calls to AWS.
