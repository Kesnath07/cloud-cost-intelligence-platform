# Local development

The application runs locally **without AWS credentials**. Tests do not need Docker or a local
PostgreSQL installation.

## Requirements

- JDK 21 or newer (the build targets Java 21 with `--release 21`)
- Docker with Compose v2, **or** a local PostgreSQL 17 server
- Optional: Python 3 to regenerate the demonstration dataset

The Maven wrapper (`./mvnw`) downloads the correct Maven version.

## Option A: Docker Compose (application and database)

```bash
cp .env.example .env              # then edit the two passwords
docker compose up --build
```

Open <http://localhost:8080> and sign in as `admin` with `APP_ADMIN_PASSWORD`. Both containers bind
to `127.0.0.1` only. Data is kept in the `postgres-data` volume (`docker compose down -v` deletes it).

## Option B: run the application with Maven

```bash
cp .env.example .env              # edit the passwords
docker compose up -d db           # or use your own PostgreSQL 17
set -a; source .env; set +a
DB_PASSWORD="$POSTGRES_PASSWORD" ./mvnw spring-boot:run
```

### Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/costplatform` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `costplatform` / empty | Database credentials |
| `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` | `admin` / generated and logged | Operator account |
| `SERVER_PORT` | `8080` | HTTP port |
| `COSTPLATFORM_REPORTING_CURRENCY` | `USD` | The only accepted currency |
| `IMPORT_MAX_FILE_SIZE` | `25MB` | Upload limit |
| `COSTPLATFORM_AWS_ENABLED` | `false` | See [aws-integration.md](aws-integration.md) |
| `COSTPLATFORM_NOTIFICATION_WEBHOOK_URL` | empty (disabled) | HTTPS webhook for new anomalies; a secret |
| `COSTPLATFORM_NOTIFICATION_MIN_SEVERITY` | `MEDIUM` | Lowest anomaly severity that is announced |

Flyway creates and migrates the schema at startup. Health: <http://localhost:8080/actuator/health>.

## Loading data

### Demonstration dataset

The dataset in `sample-data/` is **fictional** (see [sample-data/README.md](../sample-data/README.md)).
Load it with **Data Sources → Load demonstration data**, or:

```bash
APP_ADMIN_PASSWORD=... scripts/import-sample-data.sh            # default http://localhost:8080
```

While it is loaded every page shows a "Demonstration data is loaded" banner and CSV reports carry
a notice. **Remove demonstration data** deletes it again. It contains five months of daily costs
and utilization for six resources, which produces anomalies, budget results (once you create
budgets) and recommendations.

To regenerate the files (deterministic): `python3 scripts/generate_sample_data.py`.

### Your own cost data

Export daily costs into the documented CSV format (`usage_date, service, amount, currency` plus
optional `environment, account_id, region, charge_type`) and upload it on **Data Sources** or:

```bash
APP_ADMIN_PASSWORD=... scripts/import-cost-csv.sh costs.csv
```

Files with any invalid row are rejected with row-level messages; re-uploading identical content is
rejected; overlapping rows are skipped. Utilization CSV files can be uploaded on the same page.

## Tests

```bash
./mvnw verify
```

212 tests: unit tests for calculations, parsing and rules, and integration tests that start an
**embedded PostgreSQL 17 binary** (zonky `embedded-postgres`) and apply the real Flyway migrations.
No Docker daemon, database server or AWS account is required. On Apple Silicon a Maven profile adds
the arm64 PostgreSQL binary automatically.

AWS calls and webhook requests are always mocked. The JaCoCo coverage report is written to
`target/site/jacoco/index.html`; `verify` fails below 80% line coverage.

## Useful API calls

```bash
AUTH="admin:$APP_ADMIN_PASSWORD"
curl -u "$AUTH" "localhost:8080/api/costs/summary?start=2026-09-01&end=2026-09-30"
curl -u "$AUTH" "localhost:8080/api/costs/summary?service=AWS%20Lambda&environment=(untagged)"
curl -u "$AUTH" "localhost:8080/api/costs/daily?start=2026-09-01&end=2026-09-30&groupBy=service"
curl -u "$AUTH" "localhost:8080/api/costs/forecast?month=2026-09"
curl -u "$AUTH" "localhost:8080/api/anomalies?status=OPEN"
curl -u "$AUTH" -X POST "localhost:8080/api/recommendations/generate"
curl -u "$AUTH" -OJ "localhost:8080/api/reports/cost-summary.csv?start=2026-09-01&end=2026-09-30"
curl -u "$AUTH" -OJ "localhost:8080/api/reports/daily-costs.csv?start=2026-09-01&end=2026-09-30&environment=production"
curl -u "$AUTH" -OJ "localhost:8080/api/reports/anomalies.csv?start=2026-09-01&end=2026-09-30&status=OPEN"
curl -u "$AUTH" -OJ "localhost:8080/api/reports/recommendations.csv"
```

## Troubleshooting

- **Flyway reports "more than one migration with version 1"** or tests fail with class names ending
  in ` 2`: a file-sync tool (for example iCloud Drive for the Desktop folder) duplicated files in
  `target/`. Run `./mvnw clean verify`, and preferably keep the repository outside synced folders.
- **`AWS Cost Explorer integration is disabled`**: expected locally; use CSV imports or see
  [aws-integration.md](aws-integration.md).
