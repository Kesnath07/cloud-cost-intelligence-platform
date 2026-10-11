package com.cloudcost.report;

import java.time.LocalDate;

import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.analysis.CostFilter;
import com.cloudcost.anomaly.AnomalyStatus;
import com.cloudcost.common.DateRange;
import com.cloudcost.recommendation.RecommendationStatus;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSV report downloads. {@code service} and {@code environment} restrict the cost figures in the
 * same way as the cost analysis API; {@code environment=(untagged)} selects untagged costs.
 * Anomaly and recommendation exports take an optional {@code status}; without it every status
 * is included.
 */
@RestController
@RequestMapping("/api/reports")
class ReportApiController {

    private final CostReportService reports;
    private final CostAnalysisService analysis;

    ReportApiController(CostReportService reports, CostAnalysisService analysis) {
        this.reports = reports;
        this.analysis = analysis;
    }

    @GetMapping("/cost-summary.csv")
    ResponseEntity<String> summaryCsv(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String environment) {
        DateRange range = analysis.resolveRange(start, end);
        CostFilter filter = CostFilter.fromRequest(service, environment);
        return CsvResponses.attachment(CostReportService.fileName("cost-summary", range),
                reports.summaryCsv(reports.build(range, filter)));
    }

    @GetMapping("/daily-costs.csv")
    ResponseEntity<String> dailyCsv(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String environment) {
        DateRange range = analysis.resolveRange(start, end);
        CostFilter filter = CostFilter.fromRequest(service, environment);
        return CsvResponses.attachment(CostReportService.fileName("daily-costs", range),
                reports.dailyCostsCsv(range, filter));
    }

    @GetMapping("/anomalies.csv")
    ResponseEntity<String> anomaliesCsv(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(required = false) AnomalyStatus status) {
        DateRange range = analysis.resolveRange(start, end);
        return CsvResponses.attachment(CostReportService.fileName("anomalies", range),
                reports.anomaliesCsv(range, status));
    }

    @GetMapping("/recommendations.csv")
    ResponseEntity<String> recommendationsCsv(@RequestParam(required = false) RecommendationStatus status) {
        return CsvResponses.attachment(reports.fileName("recommendations"), reports.recommendationsCsv(status));
    }
}
