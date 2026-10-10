#!/usr/bin/env python3
"""Generate the fictional demonstration dataset in sample-data/.

The output is NOT real AWS spending. It is synthetic data with a few deliberate patterns
so that every analysis feature has something to show:

* a two-day spike in "EC2 - Other" (production) on 2026-09-22/23 -> cost anomalies
* a sustained ~40% increase in production RDS from 2026-09-03   -> sustained-increase recommendation
* development compute that costs the same on weekends            -> non-production scheduling review
* production EC2 instances without an Environment tag            -> untagged-spend recommendation
* a one-off S3 refund (negative amount) on 2026-07-15            -> negative adjustment handling
* a new ElastiCache cluster in staging from 2026-09-15           -> small new spend (below thresholds)

The generator is deterministic (fixed seed), so re-running it reproduces the committed files.
Usage: python3 scripts/generate_sample_data.py
"""

import csv
import random
from datetime import date, timedelta
from pathlib import Path

START = date(2026, 5, 1)
END = date(2026, 9, 30)
SEED = 20260930
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "sample-data"

# AWS documentation example account IDs: clearly not real accounts.
PROD_ACCOUNT = "111122223333"
NONPROD_ACCOUNT = "444455556666"

# (service, environment, region, base daily cost, weekend factor)
STREAMS = [
    ("Amazon Elastic Compute Cloud - Compute", "production", "us-east-1", 62.0, 0.85),
    ("Amazon Elastic Compute Cloud - Compute", "staging", "us-east-1", 14.0, 0.55),
    ("Amazon Elastic Compute Cloud - Compute", "development", "us-east-1", 11.0, 0.98),
    ("Amazon Elastic Compute Cloud - Compute", "", "us-east-1", 15.0, 1.0),
    ("Amazon Relational Database Service", "production", "us-east-1", 38.0, 1.0),
    ("Amazon Relational Database Service", "staging", "us-east-1", 9.0, 1.0),
    ("Amazon Relational Database Service", "development", "us-east-1", 6.0, 1.0),
    ("EC2 - Other", "production", "us-east-1", 18.0, 0.9),
    ("EC2 - Other", "development", "us-east-1", 3.0, 1.0),
    ("EC2 - Other", "", "us-east-1", 6.0, 1.0),
    ("Amazon Elastic Container Service", "production", "us-east-1", 24.0, 0.8),
    ("Amazon Elastic Container Service", "staging", "us-east-1", 7.0, 0.6),
    ("Amazon Elastic Container Service", "development", "us-east-1", 9.0, 0.95),
    ("Amazon Simple Storage Service", "production", "us-east-1", 9.0, 1.0),
    ("Amazon Simple Storage Service", "", "us-east-1", 4.0, 1.0),
    ("AmazonCloudWatch", "production", "us-east-1", 5.0, 0.95),
    ("AmazonCloudWatch", "staging", "us-east-1", 1.5, 1.0),
    ("AmazonCloudWatch", "development", "us-east-1", 1.2, 1.0),
    ("AmazonCloudWatch", "", "us-east-1", 2.0, 1.0),
    ("Amazon Elastic Load Balancing", "production", "us-east-1", 6.0, 1.0),
    ("Amazon Elastic Load Balancing", "staging", "us-east-1", 2.5, 1.0),
    ("AWS Lambda", "production", "us-east-1", 3.0, 0.7),
    ("Amazon DynamoDB", "production", "us-east-1", 4.0, 0.9),
    ("Amazon CloudFront", "production", "global", 7.0, 0.8),
    ("Amazon Virtual Private Cloud", "", "us-east-1", 3.5, 1.0),
    ("AWS Key Management Service", "", "us-east-1", 1.0, 1.0),
]


def daily_amount(rng, service, environment, base, weekend_factor, day):
    amount = base * (weekend_factor if day.weekday() >= 5 else 1.0)
    # Slow organic growth of storage (about 1% per month).
    if service == "Amazon Simple Storage Service":
        amount *= 1 + 0.01 * ((day - START).days / 30)
    # Sustained increase: an additional production RDS replica from 2026-09-03.
    if service == "Amazon Relational Database Service" and environment == "production" and day >= date(2026, 9, 3):
        amount *= 1.42
    amount *= rng.uniform(0.94, 1.06)
    # Short spike: NAT gateway data processing during a misconfigured batch job.
    if service == "EC2 - Other" and environment == "production":
        if day == date(2026, 9, 22):
            amount = 162.40
        elif day == date(2026, 9, 23):
            amount = 141.85
    return round(amount, 4)


def account_for(environment):
    return PROD_ACCOUNT if environment in ("production", "") else NONPROD_ACCOUNT


def generate_costs(rng):
    rows = []
    day = START
    while day <= END:
        for service, environment, region, base, weekend_factor in STREAMS:
            amount = daily_amount(rng, service, environment, base, weekend_factor, day)
            rows.append([day.isoformat(), service, f"{amount:.4f}", "USD", environment,
                         account_for(environment), region, "USAGE"])
        if day >= date(2026, 9, 15):
            amount = round(8.0 * rng.uniform(0.97, 1.03), 4)
            rows.append([day.isoformat(), "Amazon ElastiCache", f"{amount:.4f}", "USD", "staging",
                         NONPROD_ACCOUNT, "us-east-1", "USAGE"])
        if day == date(2026, 7, 15):
            rows.append([day.isoformat(), "Amazon Simple Storage Service", "-12.4000", "USD", "production",
                         PROD_ACCOUNT, "us-east-1", "REFUND"])
        day += timedelta(days=1)
    return rows


def write_csv(path, header, rows):
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(header)
        writer.writerows(rows)


def main():
    rng = random.Random(SEED)
    OUTPUT_DIR.mkdir(exist_ok=True)
    cost_rows = generate_costs(rng)
    write_csv(OUTPUT_DIR / "demo-cost-data.csv",
              ["usage_date", "service", "amount", "currency", "environment", "account_id", "region", "charge_type"],
              cost_rows)

    # Utilization for September 2026 (30 days). Costs are the resources' fictional September cost.
    utilization_rows = [
        ["i-0demo00000000001", "m6i.2xlarge", "Amazon Elastic Compute Cloud - Compute", "production", "us-east-1",
         "2026-09-01", "2026-09-30", "54.20", "91.30", "276.48", "USD"],
        ["i-0demo00000000002", "m6i.xlarge", "Amazon Elastic Compute Cloud - Compute", "development", "us-east-1",
         "2026-09-01", "2026-09-30", "4.20", "18.50", "138.24", "USD"],
        ["i-0demo00000000003", "t3.large", "Amazon Elastic Compute Cloud - Compute", "staging", "us-east-1",
         "2026-09-01", "2026-09-30", "1.10", "3.40", "60.74", "USD"],
        ["i-0demo00000000004", "c6i.large", "Amazon Elastic Compute Cloud - Compute", "production", "us-east-1",
         "2026-09-01", "2026-09-30", "35.60", "88.10", "61.20", "USD"],
        ["demo-main-db", "db.r6g.xlarge", "Amazon Relational Database Service", "production", "us-east-1",
         "2026-09-01", "2026-09-30", "41.00", "78.40", "331.20", "USD"],
        ["demo-reporting-db", "db.r6g.large", "Amazon Relational Database Service", "production", "us-east-1",
         "2026-09-01", "2026-09-30", "7.50", "22.00", "165.60", "USD"],
    ]
    write_csv(OUTPUT_DIR / "demo-utilization.csv",
              ["resource_id", "resource_type", "service", "environment", "region", "period_start", "period_end",
               "avg_cpu_percent", "max_cpu_percent", "period_cost", "currency"],
              utilization_rows)
    print(f"Wrote {len(cost_rows)} cost rows and {len(utilization_rows)} utilization rows to {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
