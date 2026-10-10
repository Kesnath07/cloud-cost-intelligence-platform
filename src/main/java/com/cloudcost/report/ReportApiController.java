package com.cloudcost.report;

import java.time.LocalDate;

import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.common.DateRange;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end) {
        DateRange range = analysis.resolveRange(start, end);
        return CsvResponses.attachment(CostReportService.fileName("cost-summary", range),
                reports.summaryCsv(reports.build(range)));
    }

    @GetMapping("/daily-costs.csv")
    ResponseEntity<String> dailyCsv(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end) {
        DateRange range = analysis.resolveRange(start, end);
        return CsvResponses.attachment(CostReportService.fileName("daily-costs", range), reports.dailyCostsCsv(range));
    }
}
