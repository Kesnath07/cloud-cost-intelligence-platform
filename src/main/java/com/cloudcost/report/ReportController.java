package com.cloudcost.report;

import java.time.LocalDate;

import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.analysis.CostFilter;
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
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(required = false) String service, @RequestParam(required = false) String environment,
            Model model) {
        DateRange range;
        try {
            range = analysis.resolveRange(start, end);
        } catch (InvalidRequestException ex) {
            model.addAttribute("errorMessage", ex.getMessage() + ". Showing the default period instead.");
            range = analysis.resolveRange(null, null);
        }
        CostFilter filter = CostFilter.fromRequest(service, environment);
        model.addAttribute("range", range);
        model.addAttribute("defaultRange", start == null && end == null && filter.isEmpty());
        model.addAttribute("filter", filter);
        model.addAttribute("services", analysis.availableServices());
        model.addAttribute("environments", analysis.availableEnvironments());
        model.addAttribute("report", reports.build(range, filter));
        model.addAttribute("coverage", analysis.coverage());
        return "reports";
    }

    @GetMapping("/cost-summary.csv")
    ResponseEntity<String> summaryCsv(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(required = false) String service, @RequestParam(required = false) String environment) {
        DateRange range = analysis.resolveRange(start, end);
        CostFilter filter = CostFilter.fromRequest(service, environment);
        return CsvResponses.attachment(CostReportService.fileName("cost-summary", range),
                reports.summaryCsv(reports.build(range, filter)));
    }

    @GetMapping("/daily-costs.csv")
    ResponseEntity<String> dailyCsv(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(required = false) String service, @RequestParam(required = false) String environment) {
        DateRange range = analysis.resolveRange(start, end);
        CostFilter filter = CostFilter.fromRequest(service, environment);
        return CsvResponses.attachment(CostReportService.fileName("daily-costs", range),
                reports.dailyCostsCsv(range, filter));
    }
}
