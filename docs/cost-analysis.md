# Cost analysis methodology

Everything on the dashboard, the API and the reports is computed from stored cost records with
the rules below. All rules are deterministic; there is no machine learning in this project.

## Periods and money

- A period is an **inclusive** range of UTC days: 2026-09-01 to 2026-09-30 is 30 days.
- The **comparison period** is the range of equal length immediately before it
  (2026-08-02 to 2026-08-31 for the example above).
- Without explicit dates the platform analyzes the **30 days ending on the latest day with stored
  data**, because Cost Explorer data lags by about a day and imported data may be historical.
- Requested ranges must have a start on or before the end and cover at most 1,096 days.
- Amounts are `BigDecimal`/`NUMERIC(19,6)`; sums are computed by PostgreSQL. Display and CSV output
  round to two decimals. Percentages are rounded half-up to two decimals.

| Figure | Formula | When undefined |
| --- | --- | --- |
| Change | `current - previous` | never |
| Change % | `(current - previous) / abs(previous) × 100` | previous = 0 → shown as "n/a" |
| Share of total | `part / total × 100` | total ≤ 0 (credits exceed charges) |
| Average per day | `total / days with data` | no days with data |

Using `abs(previous)` makes changes against a net-credit baseline readable: going from −10 to −5
is reported as +50%.

### Data gaps versus zero cost

A day is a **gap** when no cost data at all was ingested for it. Such days are drawn as dots on
the chart baseline, listed as "no data" in tables, excluded from daily averages and anomaly
baselines, and announced in a notice because the period total is understated. A day that has data
for other services but none for the selected service is a genuine **zero**.

Monthly figures flag **partial months** when the selected period covers only part of a month.

### Breakdowns

Spending is broken down by service and by environment (the environment tag; untagged spending
appears as `(untagged)`). Services that had spend only in the comparison period are kept with a
current amount of zero so decreases stay visible. *Largest cost increases* lists the five services
with the largest positive absolute change.

## Budgets

A budget has a positive limit, a recurring calendar period (monthly, quarterly starting in
January/April/July/October, or annual), an optional service and/or environment scope and a
warning threshold between 1 and 100 percent.

A budget is evaluated for the period that contains the evaluation date (by default the latest day
with data):

- **Spend** = sum of matching cost records from the first day of the period to the evaluation date.
- **Utilization** = spend / limit × 100.
- **Status**:
  - `EXCEEDED` when spend ≥ limit
  - `WARNING` when spend × 100 ≥ limit × threshold (an exact comparison, so 79.998% is never
    treated as 80% because of display rounding)
  - `NORMAL` otherwise (including negative spend caused by credits)
- **Linear run-rate projection** = spend / elapsed days × days in period. It is shown only after at
  least seven elapsed days and before the period ends, and is labeled as a projection; it ignores
  planned changes and charges billed once per month.

Example: monthly budget 500.00, spend 410.00 on day 10 → utilization 82%, status `WARNING` (with
the default 80% threshold), projection 1,230.00.

## Month-end forecast

`GET /api/costs/forecast?month=YYYY-MM` (optional `service` and `environment` filters; default month:
the month of the latest stored data) projects calendar-month spend from month-to-date data:

- **As of** = the latest day with stored data, capped at the month end.
- **Month to date** = sum of matching cost records from the first day of the month to the as-of date.
- **Linear projection** = month to date / elapsed days × days in month (the same rule as budgets).
- **Recent-rate projection** = month to date + average daily spend of the seven days ending on the
  as-of date (days with data only) × remaining days. It reacts faster than the linear projection
  when spending changed during the month.
- **Projected change** = change of the linear projection over the previous calendar month's total
  (for a complete month: of the actual total), `null` without a previous-month baseline.

Projections are withheld until seven days have elapsed and once the month is complete, where the
month-to-date figure is the actual total. A month without stored data returns zero spend and no
projections. Both projections are extrapolations: they ignore planned changes, seasonality and
charges billed once per month.

Example: 130.00 spent in the first 10 days of a 30-day month, of which 100.00 in the last seven
days → linear projection 390.00; recent-rate projection 130.00 + 14.285714 × 20 = 415.71.

### Daily series

`GET /api/costs/daily` returns the daily costs of a period as parallel arrays (`dates` and one
`amounts` array per series) for charting. `groupBy=service` or `groupBy=environment` produces one
series per value, ordered by total spend; without it a single `Total` series is returned. Amounts
are `null` on days without any stored data and zero on days with data but no matching spend, the
same gap rule as everywhere else.

## Anomaly detection

For every service and every environment, each day with data is compared with a baseline:

1. **Baseline** = the median daily cost on the days *with data* in the 14 calendar days before
   the evaluated day. The median keeps a single earlier spike from distorting the baseline, so
   consecutive spike days are all reported.
2. Days with fewer than **7** baseline days are not evaluated (not enough history).
3. A day is flagged when the increase over the baseline is **at least 25.00** *and*, if the
   baseline is positive, **at least 50%**. Both conditions are required so that large relative
   changes on tiny amounts (2.00 → 20.00) and small relative changes on large amounts
   (1,000 → 1,400) are not reported.
4. With a zero or negative baseline (new or previously offset spend) only the absolute rule applies.
5. **Severity** follows the absolute increase: low below 100.00, medium from 100.00, high from
   500.00.

Every finding stores the observed amount, baseline, increase, percentage, number of baseline days
and a plain-language reason, for example:

> Daily cost of 162.40 USD was 144.40 USD (+802.22%) above the median of the previous 14 day(s)
> with data (18.00 USD). Flagged because the increase is at least 50% and at least 25.00 USD.

Detection runs automatically after every import, removal or synchronization, for the changed days
plus the following 14 days (whose baselines include the changed days). It can also be started
manually for any range. Re-running is idempotent: figures are refreshed in place, review status
(acknowledged/dismissed with an optional note) is kept, and **open** findings that the current data
no longer supports are removed. Reviewed findings are kept as history.

All thresholds are configurable (`costplatform.anomaly.*` in `application.yml`).

### Notifications

When `COSTPLATFORM_NOTIFICATION_WEBHOOK_URL` is set (an HTTPS URL accepting `{"text": "..."}`), every
detection run that stores new findings posts one message listing up to ten of them, most severe and
largest increase first. Only findings at or above `COSTPLATFORM_NOTIFICATION_MIN_SEVERITY` (default
`MEDIUM`) and dated within the last seven days are announced, so importing historical data does not
announce old findings. Refreshed and reviewed anomalies are never announced again. Delivery is best
effort with a five-second timeout and never affects detection or ingestion.

Limitations: daily granularity only; sustained step changes stop being flagged once they become
the baseline (about a week); seasonality beyond the 14-day window (for example month-end batch
jobs) is not modeled and can produce expected findings that should be dismissed.

## Recommendations

Rules run against a snapshot of the stored data: the 30-day window ending on the latest day with
data, the 30 days before it, active budgets and the latest utilization measurement per resource.
Every recommendation contains:

| Field | Meaning |
| --- | --- |
| Title, category, severity | What to look at and how important it is |
| Affected service / resource / environment | When known |
| **Observed evidence** | Figures taken directly from stored data |
| **Interpretation (heuristic)** | What the evidence suggests and what it does **not** prove |
| Suggested action | A manual next step; the platform never changes AWS resources |
| Estimated monthly savings | Only where the data supports a figure; otherwise "Not estimated" with the reason |
| Evidence source | Billing data, budget evaluation or utilization measurements |
| Status, created / last evaluated | `OPEN`, `ACCEPTED`, `RESOLVED`, `DISMISSED` |

| Rule | Triggers when | Savings estimate |
| --- | --- | --- |
| **Sustained cost increase** | A service's spend rose ≥ 20% and ≥ 100.00 versus the previous window **and** the average daily cost in *both* halves of the current window exceeds the previous daily average by ≥ 20% (so one-off spikes, which the anomaly detector reports, are excluded). Requires ≥ 14 days with data in both windows. | Not estimated: billing data does not show which part is avoidable |
| **Always-on non-production spending** | An environment listed in `non-production-environments` costs on average ≥ 80% as much per weekend day as per weekday (≥ 10 weekdays and ≥ 4 weekend days with data; weekend average ≥ 5.00). Daily data cannot show hourly usage, so this is a review prompt. | Not estimated |
| **Untagged spending** | Spend without an environment tag is ≥ 10% of the window total and ≥ 50.00. Lists the largest untagged services. | Not applicable |
| **Budget overrun** | An active budget is `EXCEEDED` (high) or its run-rate projection reaches the limit (medium). | Not applicable |
| **Low CPU utilization** | A resource with an uploaded measurement of ≥ 14 days, ending within 90 days of the analysis date, averaged < 10% CPU with a peak < 40%. Memory, I/O and burst needs are explicitly called out as unmeasured. | Not estimated: depends on the target size |
| **Near-idle resource** | Same measurement rules, peak CPU ≤ 5%. Explicitly states that this does not prove the resource is unused. | Upper bound = supplied period cost normalized to 30 days, realized only if the resource is confirmed unneeded and removed |

**Billing data alone never produces a claim that a resource is idle, unused or oversized**; those
two rules only run for resources whose utilization was uploaded.

Recommendations are re-evaluated after every data change and every budget change, and on demand.
They are identified by a fingerprint (rule, subject and month or period), so re-evaluation updates
evidence instead of duplicating, keeps `DISMISSED`/`RESOLVED` decisions, and removes `OPEN`
recommendations whose evidence disappeared. A problem that persists into a new month produces a
new recommendation.

Severity thresholds per rule: sustained increase high ≥ 10× and medium ≥ 3× the minimum amount;
weekend spend in the window high ≥ 1,000 and medium ≥ 200; untagged share high ≥ 30% and medium
≥ 20%; utilization rules by normalized monthly cost (high ≥ 500, medium ≥ 100).

## Reports

`/reports` (UI) and `/api/reports/*.csv` export the files below. The cost summary and daily costs
exports accept the optional `service` and `environment` parameters of the cost analysis API
(`environment=(untagged)` selects untagged costs); the anomaly and recommendation exports accept an
optional `status`.

**Cost summary CSV** — one table with a constant set of columns:

`section, name, value, comparison_value, change, change_percent, percent, status, date, notes`

| `section` | `value` | `comparison_value` | `percent` | Other columns |
| --- | --- | --- | --- | --- |
| `report` | — | — | — | `name` = field (period, currency, generated_at, data notices), `notes` = value |
| `summary` | period total | comparison total | — | `change`, `change_percent` |
| `service`, `environment` | current | previous | share of total | `change`, `change_percent` |
| `budget` | spend | limit | utilization | `status`, `date` = evaluation date, `notes` = period and scope |
| `anomaly` | observed | baseline | — | `change` = increase, `status`, `date`, `notes` = severity and reason |
| `recommendation` | estimated monthly savings (empty if not estimated) | — | — | `status`, `date` = created, `notes` = severity, category, evidence, action, savings basis |

Budgets are evaluated as of the last day of the report period. If demonstration data or Cost
Explorer estimated data is included, a `report,data_notice` row says so. With a service or
environment filter, the total and the breakdowns cover matching costs only and a `report,filter`
row records the filter; budgets, anomalies and recommendations always cover the whole account.

**Daily costs CSV** — `date, service, environment, amount, currency`.

**Anomalies CSV** (`anomalies.csv?start=&end=&status=`) — every finding dated within the period,
newest first: `id, date, dimension, dimension_value, observed_amount, baseline_amount,
increase_amount, increase_percent, baseline_days, severity, status, reason, review_note,
reviewed_at, detected_at, currency`.

**Recommendations CSV** (`recommendations.csv?status=`) — every stored recommendation, most severe
first: `id, title, category, severity, status, service, environment, resource_id,
estimated_monthly_savings, savings_basis, evidence_source, evidence, suggested_action, period_start,
period_end, status_note, created_at, last_evaluated_at, currency`.

All files are RFC 4180 CSV (UTF-8, CRLF line endings). Text fields that a spreadsheet could
evaluate as a formula (starting with `=`, `+`, `-`, `@`, tab or carriage return) are prefixed with
an apostrophe; numeric fields are plain decimals, so negative amounts remain numbers.
