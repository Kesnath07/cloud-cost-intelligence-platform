package com.cloudcost.analysis;

import java.time.LocalDate;
import java.util.List;

import com.cloudcost.common.DateRange;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only cost analysis API. Dates are inclusive UTC days in ISO format. Without dates the
 * 30 days ending on the latest stored data are analyzed. {@code environment=(untagged)} selects
 * costs without an environment tag.
 */
@RestController
@RequestMapping("/api/costs")
class CostAnalysisApiController {

    private final CostAnalysisService analysis;

    CostAnalysisApiController(CostAnalysisService analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/summary")
    CostSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String environment) {
        DateRange range = analysis.resolveRange(start, end);
        return analysis.summarize(range, CostFilter.fromRequest(service, environment));
    }

    /** Daily costs as parallel arrays for charting; {@code groupBy} is {@code service} or {@code environment}. */
    @GetMapping("/daily")
    DailyCostSeries daily(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String environment) {
        DateRange range = analysis.resolveRange(start, end);
        CostDimension dimension = groupBy == null || groupBy.isBlank() ? null : CostDimension.fromParameter(groupBy);
        return analysis.dailyCostSeries(range, CostFilter.fromRequest(service, environment), dimension);
    }

    @GetMapping("/monthly")
    List<MonthlyCost> monthly(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "6") int months,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String environment) {
        LocalDate last = end != null ? end : analysis.latestDataDateOrToday();
        return analysis.monthlyTrend(last, Math.clamp(months, 1, 36), CostFilter.fromRequest(service, environment));
    }

    @GetMapping("/coverage")
    DataCoverage coverage() {
        return analysis.coverage();
    }

    @GetMapping("/dimensions")
    Dimensions dimensions() {
        return new Dimensions(analysis.availableServices(), analysis.availableEnvironments());
    }

    record Dimensions(List<String> services, List<String> environments) {
    }
}
