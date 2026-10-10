package com.cloudcost.web;

import java.time.LocalDate;

import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.analysis.CostFilter;
import com.cloudcost.analysis.CostSummary;
import com.cloudcost.common.DateRange;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Cost Explorer (filterable daily and monthly costs) and the service breakdown page. */
@Controller
class ExplorerController {

    private final CostAnalysisService analysis;
    private final ChartFactory charts;

    ExplorerController(CostAnalysisService analysis, ChartFactory charts) {
        this.analysis = analysis;
        this.charts = charts;
    }

    @GetMapping("/explorer")
    String explorer(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(required = false) String service, @RequestParam(required = false) String environment,
            Model model) {
        DateRange range = PeriodSelection.resolve(analysis, start, end, model);
        CostFilter filter = CostFilter.fromRequest(service, environment);
        CostSummary summary = analysis.summarize(range, filter);
        model.addAttribute("summary", summary);
        model.addAttribute("filter", filter);
        model.addAttribute("dailyChart", charts.daily(summary.daily()));
        model.addAttribute("services", analysis.availableServices());
        model.addAttribute("environments", analysis.availableEnvironments());
        model.addAttribute("coverage", analysis.coverage());
        return "explorer";
    }

    @GetMapping("/services")
    String services(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end, Model model) {
        DateRange range = PeriodSelection.resolve(analysis, start, end, model);
        model.addAttribute("summary", analysis.summarize(range, CostFilter.NONE));
        model.addAttribute("coverage", analysis.coverage());
        return "services";
    }
}
