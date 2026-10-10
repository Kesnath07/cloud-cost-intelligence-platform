# Sample data

> **Demonstration data only.** The files in this directory are synthetic. They were produced by
> `scripts/generate_sample_data.py` and do not describe real AWS spending, real resources or real
> accounts (the account IDs are the example IDs used in AWS documentation).

| File | Contents |
| --- | --- |
| `demo-cost-data.csv` | Daily costs, 2026-05-01 to 2026-09-30, for 13 services across `production`, `staging`, `development` and untagged spend |
| `demo-utilization.csv` | Average and peak CPU for six fictional resources during September 2026 |

The application packages these files and imports them when you choose **Load demonstration data**
on the *Data Sources* page (or call `POST /api/imports/demo`). Imported demo records are stored with the
source `DEMO_SAMPLE`, and every page shows a banner while they are present. **Remove demonstration
data** deletes them again.

The data contains deliberate patterns: a two-day spike in production `EC2 - Other`, a sustained increase
in production RDS from 2026-09-03, development compute that runs through weekends, production EC2 spend
without an `Environment` tag, a one-off S3 refund and a small new ElastiCache cluster.

To regenerate the files (deterministic, so the output is identical):

```bash
python3 scripts/generate_sample_data.py
```

## Cost CSV format

UTF-8, comma-separated, with a header row. Column names are case-insensitive, in any order.

| Column | Required | Description |
| --- | --- | --- |
| `usage_date` | yes | UTC usage day, `YYYY-MM-DD`; not in the future |
| `service` | yes | AWS service name; common aliases (`AmazonEC2`, `EC2`, `S3`, ...) are mapped to Cost Explorer names |
| `amount` | yes | Decimal with `.` separator, no currency symbol or thousands separator; stored with 6 decimal places |
| `currency` | yes | Must equal the configured reporting currency (default `USD`); no conversion is performed |
| `environment` | no | Value of the environment tag; lower-cased, with `prod`/`dev`/`stg` mapped to `production`/`development`/`staging`; empty means untagged |
| `account_id` | no | 12-digit AWS account ID |
| `region` | no | AWS region code such as `us-east-1`, or `global` |
| `charge_type` | no | `USAGE` (default), `TAX`, `CREDIT`, `REFUND` or `NET`. Credits and refunds must be zero or negative; usage and tax must not be negative |

A file is imported only if every row is valid; otherwise it is rejected with row-level messages. A row whose
combination of date, account, service, region, environment and charge type already exists (for the same
source) is skipped, which makes re-importing overlapping exports safe. Uploading byte-identical content a
second time is rejected outright.

## Utilization CSV format

| Column | Required | Description |
| --- | --- | --- |
| `resource_id` | yes | Instance ID, DB identifier or other resource identifier |
| `service` | yes | AWS service name (same normalization as above) |
| `period_start`, `period_end` | yes | Measurement period, inclusive, `YYYY-MM-DD` |
| `avg_cpu_percent`, `max_cpu_percent` | yes | 0–100; the average cannot exceed the maximum |
| `resource_type` | no | For example `m6i.xlarge` or `db.r6g.large` |
| `environment`, `region` | no | As above |
| `period_cost` | no | Cost of the resource for the period; enables a savings upper bound for near-idle resources |
| `currency` | if `period_cost` is set | Reporting currency |
