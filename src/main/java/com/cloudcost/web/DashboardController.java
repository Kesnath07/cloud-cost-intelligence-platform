package com.cloudcost.web;

import java.time.LocalDate;
import java.util.List;

import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.analysis.CostFilter;
import com.cloudcost.analysis.CostSummary;
import com.cloudcost.anomaly.AnomalyDetectionService;
import com.cloudcost.anomaly.AnomalyStatus;
import com.cloudcost.anomaly.CostAnomaly;
import com.cloudcost.budget.BudgetEvaluation;
import com.cloudcost.budget.BudgetService;
import com.cloudcost.budget.BudgetStatus;
import com.cloudcost.common.DateRange;
import com.cloudcost.recommendation.RecommendationService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The overview dashboard: totals, trends, breakdowns, budgets and open findings for one period. */
@Controller
class DashboardController {

    static final int TOP_ITEMS = 8;
    private static final int TREND_MONTHS = 6;
    private static final int RECENT_ANOMALIES = 5;

    private final CostAnalysisService analysis;
    private final BudgetService budgets;
    private final AnomalyDetectionService anomalies;
    private final RecommendationService recommendations;
    private final ChartFactory charts;

    DashboardController(CostAnalysisService analysis, BudgetService budgets, AnomalyDetectionService anomalies,
            RecommendationService recommendations, ChartFactory charts) {
        this.analysis = analysis;
        this.budgets = budgets;
        this.anomalies = anomalies;
        this.recommendations = recommendations;
        this.charts = charts;
    }

    @GetMapping("/")
    String overview(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate end, Model model) {
        DateRange range = PeriodSelection.resolve(analysis, start, end, model);
        CostSummary summary = analysis.summarize(range, CostFilter.NONE);
        model.addAttribute("summary", summary);
        model.addAttribute("coverage", analysis.coverage());
        model.addAttribute("dailyChart", charts.daily(summary.daily()));
        model.addAttribute("monthlyChart", charts.monthly(analysis.monthlyTrend(range.end(), TREND_MONTHS,
                CostFilter.NONE)));
        model.addAttribute("topServices", Breakdowns.topWithOther(summary.byService(), TOP_ITEMS, summary.total()));
        model.addAttribute("environments", summary.byEnvironment());

        LocalDate budgetDate = budgets.defaultAsOf();
        List<BudgetEvaluation> evaluations = budgets.evaluateActive(budgetDate);
        model.addAttribute("budgetDate", budgetDate);
        model.addAttribute("budgets", evaluations);
        model.addAttribute("budgetsExceeded", evaluations.stream().filter(e -> e.status() == BudgetStatus.EXCEEDED).count());
        model.addAttribute("budgetsWarning", evaluations.stream().filter(e -> e.status() == BudgetStatus.WARNING).count());

        List<CostAnomaly> periodAnomalies = anomalies.findInRange(range).stream()
                .filter(a -> a.getStatus() == AnomalyStatus.OPEN)
                .toList();
        model.addAttribute("periodAnomalyCount", periodAnomalies.size());
        model.addAttribute("recentAnomalies", periodAnomalies.stream().limit(RECENT_ANOMALIES).toList());
        model.addAttribute("openAnomalies", anomalies.countOpen());
        model.addAttribute("openRecommendations", recommendations.countOpen());
        return "overview";
    }
}
