# Architecture

The platform is a **modular monolith**: one Spring Boot application, one PostgreSQL database,
packages with clear responsibilities, and no message brokers or caches. The workload (periodic
imports, interactive analysis of at most a few million daily rows) does not justify distributed
components.

## Components

```mermaid
flowchart LR
    subgraph Sources
        CSV[Cost CSV upload]
        DEMO[Demonstration dataset]
        UTIL[Utilization CSV]
        CE[AWS Cost Explorer<br/>GetCostAndUsage]
    end

    subgraph App[Spring Boot application]
        ING[ingestion<br/>parse, validate, normalize]
        AWS[aws<br/>Cost Explorer sync]
        DATA[(costdata<br/>records, jobs, utilization)]
        AN[analysis<br/>totals, trends, breakdowns]
        BUD[budget<br/>limits and status]
        ANO[anomaly<br/>rule-based detection]
        REC[recommendation<br/>evidence-based rules]
        REP[report<br/>CSV export]
        WEB[web<br/>Thymeleaf UI]
        API[REST API /api]
    end

    PG[(PostgreSQL)]

    CSV --> ING
    DEMO --> ING
    UTIL --> ING
    CE --> AWS
    ING --> DATA
    AWS --> DATA
    DATA --- PG
    DATA --> AN
    AN --> BUD
    AN --> ANO
    AN --> REC
    BUD --> REC
    AN --> REP
    BUD --> REP
    ANO --> REP
    REC --> REP
    AN --> WEB
    BUD --> WEB
    ANO --> WEB
    REC --> WEB
    REP --> WEB
    AN --> API
```

| Package | Responsibility |
| --- | --- |
| `config`, `common` | Clock (UTC), security, error handling, `DateRange`, `MoneyMath` |
| `costdata` | Entities and repositories for import jobs, cost records and utilization; the data-change event |
| `ingestion` | CSV parsing and validation, normalization, idempotent bulk writes, demonstration dataset, Data Sources page |
| `aws` | Optional Cost Explorer client, synchronization, error translation, scheduled refresh |
| `analysis` | Aggregation queries (PostgreSQL `NUMERIC` sums) and period comparisons |
| `budget` | Budgets, evaluation and status rules |
| `anomaly` | Detector, detection runs and review workflow |
| `recommendation` | Rule interface, five rules, engine and reconciliation |
| `report` | CSV writer and report exports |
| `web` | Dashboard, Cost Explorer, Service Breakdown, server-rendered SVG charts |

## Data flow

```mermaid
sequenceDiagram
    actor User
    participant Ingestion
    participant DB as PostgreSQL
    participant Anomalies
    participant Recommendations

    User->>Ingestion: Upload CSV / run Cost Explorer sync
    Ingestion->>Ingestion: Parse, validate every row, normalize
    alt any invalid row
        Ingestion->>DB: Record FAILED import job (no data written)
        Ingestion-->>User: Row-level errors
    else valid
        Ingestion->>DB: One transaction: job + INSERT ... ON CONFLICT DO NOTHING
        Ingestion-->>Anomalies: CostDataChangedEvent(range)
        Anomalies->>DB: Re-detect affected days, reconcile findings
        Ingestion-->>Recommendations: CostDataChangedEvent(range)
        Recommendations->>DB: Re-evaluate rules, reconcile by fingerprint
        Ingestion-->>User: Inserted / skipped counts
    end
```

Derived data (anomalies and recommendations) is recomputed from stored cost data after every
change, so it never drifts from the figures it is based on. Budget changes trigger a recommendation
re-evaluation after the budget transaction commits.

## Data model

```mermaid
erDiagram
    COST_IMPORT_JOB ||--o{ COST_RECORD : writes
    COST_IMPORT_JOB ||--o{ RESOURCE_UTILIZATION : writes
    COST_IMPORT_JOB {
        bigint id
        string source "CSV_UPLOAD, DEMO_SAMPLE, AWS_COST_EXPLORER"
        string data_type "COST, UTILIZATION"
        string status "RUNNING, COMPLETED, FAILED, REMOVED"
        string file_checksum "SHA-256"
        date period_start
        date period_end
        int records_inserted
        int records_skipped
        int records_replaced
    }
    COST_RECORD {
        date usage_date
        string source
        string account_id
        string service_name
        string region
        string environment "'' = untagged"
        string charge_type
        numeric amount "19,6"
        string currency
        boolean estimated
    }
    RESOURCE_UTILIZATION {
        string resource_id
        date period_start
        date period_end
        numeric avg_cpu_percent
        numeric max_cpu_percent
        numeric period_cost
    }
    COST_BUDGET {
        string name "unique, case-insensitive"
        numeric limit_amount
        string period "MONTHLY, QUARTERLY, ANNUAL"
        string service_name "null = all"
        string environment "null = all"
        int warning_threshold_percent
    }
    COST_ANOMALY {
        string dimension "SERVICE, ENVIRONMENT"
        string dimension_value
        date anomaly_date
        numeric observed_amount
        numeric baseline_amount
        string severity
        string status "OPEN, ACKNOWLEDGED, DISMISSED"
    }
    OPTIMIZATION_RECOMMENDATION {
        string fingerprint "unique"
        string category
        string severity
        string evidence_source
        text evidence
        numeric estimated_monthly_savings "nullable"
        string status "OPEN, ACCEPTED, RESOLVED, DISMISSED"
    }
```

### Uniqueness and ownership rules

| Rule | Enforced by |
| --- | --- |
| One cost amount per source, day, account, service, region, environment and charge type | `uk_cost_record_natural_key` |
| The same file content cannot be imported twice while its data is present | partial unique index on `(data_type, file_checksum) WHERE status = 'COMPLETED'` |
| At most one Cost Explorer synchronization running, across all instances | partial unique index on `source WHERE status = 'RUNNING'` |
| One anomaly per dimension value and day | `uk_cost_anomaly` |
| One recommendation per rule, subject and period | `uk_optimization_recommendation_fingerprint` |
| Budget names are unique ignoring case | unique index on `lower(name)` |

Each cost record belongs to the import job that wrote it. Removing a CSV or demonstration import
deletes exactly the rows it inserted; rows it skipped as duplicates belong to the earlier job.
A Cost Explorer synchronization replaces all previously synchronized rows in its date range.

### Conventions

- **Time**: all timestamps are `TIMESTAMPTZ` written in UTC; usage dates are UTC calendar days, as
  reported by AWS. The JVM runs with `user.timezone=UTC` in the container.
- **Money**: `NUMERIC(19,6)` in the database and `BigDecimal` in Java; sums are computed by
  PostgreSQL. Only percentages are rounded (two decimals, half-up). One reporting currency.
- **Schema**: Flyway migrations `V1`–`V5`; Hibernate runs with `ddl-auto=validate`.
- **Writes**: bulk inserts use JDBC batches with `ON CONFLICT`; JPA is used for everything else.

## Deployment architecture

```mermaid
flowchart TB
    User((Operator)) -->|HTTPS, allowed CIDRs only| ALB
    subgraph VPC
        subgraph Public[Public subnets]
            ALB[Application Load Balancer]
            NAT[NAT gateway<br/>optional]
        end
        subgraph AppNet[Private application subnets]
            ECS[ECS Fargate task<br/>Spring Boot]
        end
        subgraph DbNet[Isolated database subnets]
            RDS[(RDS PostgreSQL 17<br/>encrypted, TLS only)]
        end
    end
    ALB --> ECS
    ECS --> RDS
    ECS -->|HTTPS| NAT
    NAT --> AWSAPI[ECR, CloudWatch Logs,<br/>Secrets Manager, Cost Explorer]
    SM[Secrets Manager<br/>DB + admin passwords] -.->|injected at start| ECS
    GH[GitHub Actions] -->|OIDC role| ECR[ECR] & ECSAPI[ECS API]
```

See [deployment.md](deployment.md) for details and cost estimates.
