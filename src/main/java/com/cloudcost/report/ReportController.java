package com.cloudcost.report;

import java.time.LocalDate;

import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.InvalidRequestException;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/reports")
class ReportController {

    private final CostReportService reports;
    private final CostAnalysisService analysis;

    ReportController(CostReportService reports, CostAnalysisService analysis) {
        this.reports = reports;
        this.analysis = analysis;
    }

    @GetMapping
    String reports(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end, Model model) {
        DateRange range;
        try {
            range = analysis.resolveRange(start, end);
        } catch (InvalidRequestException ex) {
            model.addAttribute("errorMessage", ex.getMessage() + ". Showing the default period instead.");
            range = analysis.resolveRange(null, null);
        }
        model.addAttribute("range", range);
        model.addAttribute("defaultRange", start == null && end == null);
        model.addAttribute("report", reports.build(range));
        model.addAttribute("coverage", analysis.coverage());
        return "reports";
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
