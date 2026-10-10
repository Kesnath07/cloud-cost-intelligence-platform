package com.cloudcost.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.cloudcost.analysis.CostAggregationRepository;
import com.cloudcost.analysis.CostAnalysisService;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.NotFoundException;
import com.cloudcost.config.PlatformProperties;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BudgetService {

    /** Projections from fewer days are too noisy to be useful. */
    public static final int MIN_DAYS_FOR_PROJECTION = 7;

    private final BudgetRepository budgets;
    private final CostAggregationRepository aggregations;
    private final CostAnalysisService analysis;
    private final PlatformProperties platformProperties;
    private final Clock clock;

    public BudgetService(BudgetRepository budgets, CostAggregationRepository aggregations,
            CostAnalysisService analysis, PlatformProperties platformProperties, Clock clock) {
        this.budgets = budgets;
        this.aggregations = aggregations;
        this.analysis = analysis;
        this.platformProperties = platformProperties;
        this.clock = clock;
    }

    public List<CostBudget> list() {
        return budgets.findAllByOrderByNameAsc();
    }

    public CostBudget get(long id) {
        return budgets.findById(id).orElseThrow(() -> NotFoundException.of("Budget", id));
    }

    @Transactional
    public CostBudget create(BudgetForm form) {
        if (budgets.existsByNameIgnoreCase(form.getName().strip())) {
            throw new DuplicateBudgetNameException(form.getName().strip());
        }
        CostBudget budget = new CostBudget(platformProperties.reportingCurrency(), Instant.now(clock));
        budget.apply(form, Instant.now(clock));
        return save(budget);
    }

    @Transactional
    public CostBudget update(long id, BudgetForm form) {
        CostBudget budget = get(id);
        if (budgets.existsByNameIgnoreCaseAndIdNot(form.getName().strip(), id)) {
            throw new DuplicateBudgetNameException(form.getName().strip());
        }
        budget.apply(form, Instant.now(clock));
        return save(budget);
    }

    @Transactional
    public void delete(long id) {
        budgets.delete(get(id));
    }

    /**
     * Budgets are evaluated as of the latest day with stored cost data (Cost Explorer data lags
     * by about a day, and imported data may be historical), or today when no data is stored.
     */
    public LocalDate defaultAsOf() {
        LocalDate today = LocalDate.now(clock);
        LocalDate latest = analysis.latestDataDateOrToday();
        return latest.isAfter(today) ? today : latest;
    }

    public List<BudgetEvaluation> evaluateActive(LocalDate asOf) {
        return budgets.findAllByActiveTrueOrderByNameAsc().stream().map(budget -> evaluate(budget, asOf)).toList();
    }

    public BudgetEvaluation evaluate(CostBudget budget, LocalDate asOf) {
        DateRange period = budget.getPeriod().periodContaining(asOf);
        DateRange elapsed = DateRange.of(period.start(), asOf);
        BigDecimal spend = aggregations.total(elapsed, budget.filter());
        BigDecimal limit = budget.getLimitAmount();
        long daysElapsed = elapsed.days();
        long daysInPeriod = period.days();

        BigDecimal projected = null;
        BigDecimal projectedUtilization = null;
        if (daysElapsed >= MIN_DAYS_FOR_PROJECTION && daysElapsed < daysInPeriod) {
            projected = spend.multiply(BigDecimal.valueOf(daysInPeriod))
                    .divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
            projectedUtilization = BudgetEvaluator.utilizationPercent(projected, limit);
        }

        return new BudgetEvaluation(budget.getId(), budget.getName(), budget.getPeriod(), period, asOf, limit, spend,
                limit.subtract(spend), BudgetEvaluator.utilizationPercent(spend, limit),
                budget.getWarningThresholdPercent(),
                BudgetEvaluator.status(spend, limit, budget.getWarningThresholdPercent()), daysElapsed, daysInPeriod,
                aggregations.datesWithData(elapsed).size(), projected, projectedUtilization,
                budget.scopeDescription());
    }

    private CostBudget save(CostBudget budget) {
        try {
            return budgets.saveAndFlush(budget);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateBudgetNameException(budget.getName());
        }
    }
}
