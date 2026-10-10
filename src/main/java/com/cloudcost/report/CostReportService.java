package com.cloudcost.report;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import com.cloudcost.analysis.BreakdownItem;
import com.cloudcost.analysis.CostAggregationRepository;
import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.analysis.CostFilter;
import com.cloudcost.analysis.CostSummary;
import com.cloudcost.anomaly.AnomalyDetectionService;
import com.cloudcost.anomaly.CostAnomaly;
import com.cloudcost.budget.BudgetEvaluation;
import com.cloudcost.budget.BudgetService;
import com.cloudcost.common.DateRange;
import com.cloudcost.config.PlatformProperties;
import com.cloudcost.costdata.DemoDataStatus;
import com.cloudcost.recommendation.OptimizationRecommendation;
import com.cloudcost.recommendation.RecommendationService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles cost reports and renders them as CSV. */
@Service
@Transactional(readOnly = true)
public class CostReportService {

    static final String[] SUMMARY_COLUMNS = {"section", "name", "value", "comparison_value", "change",
        "change_percent", "percent", "status", "date", "notes"};

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

    public CostReport build(DateRange range) {
        CostSummary summary = analysis.summarize(range, CostFilter.NONE);
        List<BudgetEvaluation> evaluations = budgets.evaluateActive(range.end());
        List<CostAnomaly> periodAnomalies = anomalies.findInRange(range);
        List<OptimizationRecommendation> active = recommendations.findActive().stream()
                .sorted(Comparator.comparing(OptimizationRecommendation::getSeverity).reversed()
                        .thenComparing(OptimizationRecommendation::getTitle))
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

    /** Daily cost per service and environment, one row per combination with spend. */
    public String dailyCostsCsv(DateRange range) {
        CsvWriter csv = new CsvWriter().header("date", "service", "environment", "amount", "currency");
        for (CostAggregationRepository.DailyServiceEnvironmentTotal row
                : aggregations.dailyTotalsByServiceAndEnvironment(range)) {
            csv.text(row.date().toString()).text(row.service())
                    .text(row.environment().isEmpty() ? CostFilter.UNTAGGED_LABEL : row.environment())
                    .amount(row.amount()).text(platformProperties.reportingCurrency()).endRow();
        }
        return csv.toString();
    }

    public static String fileName(String prefix, DateRange range) {
        return prefix + "-" + range.start() + "-to-" + range.end() + ".csv";
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
