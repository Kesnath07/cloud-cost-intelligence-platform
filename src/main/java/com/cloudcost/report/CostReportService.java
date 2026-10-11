package com.cloudcost.report;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.cloudcost.analysis.BreakdownItem;
import com.cloudcost.analysis.CostAggregationRepository;
import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.analysis.CostFilter;
import com.cloudcost.analysis.CostSummary;
import com.cloudcost.anomaly.AnomalyDetectionService;
import com.cloudcost.anomaly.AnomalyStatus;
import com.cloudcost.anomaly.CostAnomaly;
import com.cloudcost.budget.BudgetEvaluation;
import com.cloudcost.budget.BudgetService;
import com.cloudcost.common.DateRange;
import com.cloudcost.config.PlatformProperties;
import com.cloudcost.costdata.DemoDataStatus;
import com.cloudcost.recommendation.OptimizationRecommendation;
import com.cloudcost.recommendation.RecommendationService;
import com.cloudcost.recommendation.RecommendationStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles cost reports and renders them as CSV. */
@Service
@Transactional(readOnly = true)
public class CostReportService {

    static final String[] SUMMARY_COLUMNS = {"section", "name", "value", "comparison_value", "change",
        "change_percent", "percent", "status", "date", "notes"};

    static final String[] ANOMALY_COLUMNS = {"id", "date", "dimension", "dimension_value", "observed_amount",
        "baseline_amount", "increase_amount", "increase_percent", "baseline_days", "severity", "status", "reason",
        "review_note", "reviewed_at", "detected_at", "currency"};

    static final String[] RECOMMENDATION_COLUMNS = {"id", "title", "category", "severity", "status", "service",
        "environment", "resource_id", "estimated_monthly_savings", "savings_basis", "evidence_source", "evidence",
        "suggested_action", "period_start", "period_end", "status_note", "created_at", "last_evaluated_at",
        "currency"};

    private final CostAnalysisService analysis;
    private final CostAggregationRepository aggregations;
    private final BudgetService budgets;
    private final AnomalyDetectionService anomalies;
    private final RecommendationService recommendations;
    private final DemoDataStatus demoDataStatus;
    private final PlatformProperties platformProperties;
    private final Clock clock;

    public CostReportService(CostAnalysisService analysis, CostAggregationRepository aggregations,
            BudgetService budgets, AnomalyDetectionService anomalies, RecommendationService recommendations,
            DemoDataStatus demoDataStatus, PlatformProperties platformProperties, Clock clock) {
        this.analysis = analysis;
        this.aggregations = aggregations;
        this.budgets = budgets;
        this.anomalies = anomalies;
        this.recommendations = recommendations;
        this.demoDataStatus = demoDataStatus;
        this.platformProperties = platformProperties;
        this.clock = clock;
    }

    /**
     * Builds the report for a period. The filter scopes the cost figures (totals, breakdowns);
     * budgets, anomalies and recommendations always cover the whole account.
     */
    public CostReport build(DateRange range, CostFilter filter) {
        CostSummary summary = analysis.summarize(range, filter);
        List<BudgetEvaluation> evaluations = budgets.evaluateActive(range.end());
        List<CostAnomaly> periodAnomalies = anomalies.findInRange(range);
        List<OptimizationRecommendation> active = recommendations.findActive().stream()
                .sorted(RecommendationService.MOST_SEVERE_FIRST)
                .toList();
        return new CostReport(Instant.now(clock), platformProperties.reportingCurrency(), demoDataStatus.present(),
                summary, evaluations, periodAnomalies, active);
    }

    /** The cost summary report as one CSV table in long format; see docs/cost-analysis.md for the layout. */
    public String summaryCsv(CostReport report) {
        CostSummary summary = report.summary();
        CsvWriter csv = new CsvWriter().header(SUMMARY_COLUMNS);
        info(csv, "report_title", "Cloud Cost Optimization Platform - cost summary");
        info(csv, "generated_at", DateTimeFormatter.ISO_INSTANT.format(report.generatedAt()));
        info(csv, "period", summary.range().toString());
        info(csv, "comparison_period", summary.previousRange().toString());
        if (!summary.filter().isEmpty()) {
            info(csv, "filter", describe(summary.filter())
                    + "; budgets, anomalies and recommendations are not filtered");
        }
        info(csv, "currency", report.currency());
        info(csv, "days_with_data", summary.daysWithData() + " of " + summary.range().days());
        if (report.includesDemonstrationData()) {
            info(csv, "data_notice", "Includes fictional demonstration data; not real AWS spending");
        }
        if (summary.containsEstimatedData()) {
            info(csv, "data_notice", "Includes figures that AWS Cost Explorer marks as estimated");
        }

        csv.text("summary").text("total_cost").amount(summary.total()).amount(summary.previousTotal())
                .amount(summary.change()).number(summary.changePercent()).text("").text("").text("").text("")
                .endRow();
        breakdown(csv, "service", summary.byService());
        breakdown(csv, "environment", summary.byEnvironment());

        for (BudgetEvaluation budget : report.budgets()) {
            csv.text("budget").text(budget.name()).amount(budget.spend()).amount(budget.limit()).text("")
                    .text("").number(budget.utilizationPercent()).text(budget.status().name())
                    .text(budget.asOf().toString())
                    .text(budget.periodType().label() + " period " + budget.period() + "; scope: " + budget.scope()
                            + "; value = spend, comparison_value = limit, percent = utilization")
                    .endRow();
        }
        for (CostAnomaly anomaly : report.anomalies()) {
            String name = anomaly.getDimension().label() + ": " + (anomaly.getDimensionValue().isEmpty()
                    ? CostFilter.UNTAGGED_LABEL : anomaly.getDimensionValue());
            csv.text("anomaly").text(name).amount(anomaly.getObservedAmount()).amount(anomaly.getBaselineAmount())
                    .amount(anomaly.getIncreaseAmount()).number(anomaly.getIncreasePercent()).text("")
                    .text(anomaly.getStatus().name()).text(anomaly.getAnomalyDate().toString())
                    .text(anomaly.getSeverity() + " severity. " + anomaly.getReason())
                    .endRow();
        }
        for (OptimizationRecommendation recommendation : report.recommendations()) {
            csv.text("recommendation").text(recommendation.getTitle())
                    .amount(recommendation.getEstimatedMonthlySavings()).text("").text("").text("").text("")
                    .text(recommendation.getStatus().name())
                    .text(recommendation.getCreatedAt() == null ? "" : DateTimeFormatter.ISO_INSTANT
                            .format(recommendation.getCreatedAt()))
                    .text(recommendation.getSeverity() + " severity; " + recommendation.getCategory().label()
                            + "; evidence: " + String.join(" ", recommendation.evidenceLines()) + " Action: "
                            + recommendation.getSuggestedAction() + " Savings: " + recommendation.getSavingsBasis())
                    .endRow();
        }
        return csv.toString();
    }

    /** Daily cost per service and environment, one row per combination with spend matching the filter. */
    public String dailyCostsCsv(DateRange range, CostFilter filter) {
        CsvWriter csv = new CsvWriter().header("date", "service", "environment", "amount", "currency");
        for (CostAggregationRepository.DailyServiceEnvironmentTotal row
                : aggregations.dailyTotalsByServiceAndEnvironment(range, filter)) {
            csv.text(row.date().toString()).text(row.service())
                    .text(row.environment().isEmpty() ? CostFilter.UNTAGGED_LABEL : row.environment())
                    .amount(row.amount()).text(platformProperties.reportingCurrency()).endRow();
        }
        return csv.toString();
    }

    /** Anomaly findings dated within the period, newest first; {@code status} {@code null} means any status. */
    public String anomaliesCsv(DateRange range, AnomalyStatus status) {
        CsvWriter csv = new CsvWriter().header(ANOMALY_COLUMNS);
        for (CostAnomaly anomaly : anomalies.findInRange(range)) {
            if (status != null && anomaly.getStatus() != status) {
                continue;
            }
            csv.number(anomaly.getId()).text(anomaly.getAnomalyDate().toString())
                    .text(anomaly.getDimension().label())
                    .text(anomaly.getDimensionValue().isEmpty() ? CostFilter.UNTAGGED_LABEL
                            : anomaly.getDimensionValue())
                    .amount(anomaly.getObservedAmount()).amount(anomaly.getBaselineAmount())
                    .amount(anomaly.getIncreaseAmount()).number(anomaly.getIncreasePercent())
                    .number(anomaly.getBaselineDays()).text(anomaly.getSeverity().name())
                    .text(anomaly.getStatus().name()).text(anomaly.getReason()).text(anomaly.getReviewNote())
                    .text(instant(anomaly.getReviewedAt())).text(instant(anomaly.getDetectedAt()))
                    .text(platformProperties.reportingCurrency())
                    .endRow();
        }
        return csv.toString();
    }

    /** Stored recommendations, most severe first; {@code status} {@code null} means any status. */
    public String recommendationsCsv(RecommendationStatus status) {
        CsvWriter csv = new CsvWriter().header(RECOMMENDATION_COLUMNS);
        for (OptimizationRecommendation recommendation : recommendations.findAll(status)) {
            csv.number(recommendation.getId()).text(recommendation.getTitle())
                    .text(recommendation.getCategory().label()).text(recommendation.getSeverity().name())
                    .text(recommendation.getStatus().name()).text(recommendation.getServiceName())
                    .text(recommendation.getEnvironment()).text(recommendation.getResourceId())
                    .amount(recommendation.getEstimatedMonthlySavings()).text(recommendation.getSavingsBasis())
                    .text(recommendation.getEvidenceSource().name())
                    .text(String.join(" ", recommendation.evidenceLines()))
                    .text(recommendation.getSuggestedAction())
                    .text(date(recommendation.getPeriodStart())).text(date(recommendation.getPeriodEnd()))
                    .text(recommendation.getStatusNote()).text(instant(recommendation.getCreatedAt()))
                    .text(instant(recommendation.getLastEvaluatedAt()))
                    .text(platformProperties.reportingCurrency())
                    .endRow();
        }
        return csv.toString();
    }

    /** File name for an export that reflects the state on {@code date} rather than a period. */
    public String fileName(String prefix) {
        return prefix + "-" + LocalDate.now(clock) + ".csv";
    }

    public static String fileName(String prefix, DateRange range) {
        return prefix + "-" + range.start() + "-to-" + range.end() + ".csv";
    }

    /** Human-readable form of a non-empty filter, for example {@code service: AWS Lambda; environment: (untagged)}. */
    static String describe(CostFilter filter) {
        List<String> parts = new ArrayList<>();
        if (filter.service() != null) {
            parts.add("service: " + filter.service());
        }
        if (filter.environment() != null) {
            parts.add("environment: " + filter.environmentParameter());
        }
        return String.join("; ", parts);
    }

    private static String instant(Instant instant) {
        return instant == null ? "" : DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    private static String date(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static void info(CsvWriter csv, String name, String notes) {
        csv.text("report").text(name).text("").text("").text("").text("").text("").text("").text("").text(notes)
                .endRow();
    }

    private static void breakdown(CsvWriter csv, String section, List<BreakdownItem> items) {
        for (BreakdownItem item : items) {
            String name = section.equals("environment") && item.name().isEmpty() ? CostFilter.UNTAGGED_LABEL
                    : item.name();
            csv.text(section).text(name).amount(item.amount()).amount(item.previousAmount()).amount(item.change())
                    .number(item.changePercent()).number(item.sharePercent()).text("").text("").text("")
                    .endRow();
        }
    }
}
