package com.cloudcost.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
class BudgetApiController {

    private final BudgetService budgets;

    BudgetApiController(BudgetService budgets) {
        this.budgets = budgets;
    }

    record BudgetResponse(long id, String name, BigDecimal limitAmount, String currency,
            BudgetPeriod period, String service, String environment, int warningThresholdPercent, boolean active,
            BudgetEvaluation evaluation) {
    }

    @GetMapping
    List<BudgetResponse> list(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate asOf) {
        LocalDate date = asOf != null ? asOf : budgets.defaultAsOf();
        return budgets.list().stream().map(budget -> response(budget, date)).toList();
    }

    @GetMapping("/{id}")
    BudgetResponse get(@PathVariable long id,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate asOf) {
        return response(budgets.get(id), asOf != null ? asOf : budgets.defaultAsOf());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BudgetResponse create(@Valid @RequestBody BudgetForm form) {
        return response(budgets.create(form), budgets.defaultAsOf());
    }

    @PutMapping("/{id}")
    BudgetResponse update(@PathVariable long id, @Valid @RequestBody BudgetForm form) {
        return response(budgets.update(id, form), budgets.defaultAsOf());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        budgets.delete(id);
    }

    private BudgetResponse response(CostBudget budget, LocalDate asOf) {
        return new BudgetResponse(budget.getId(), budget.getName(), budget.getLimitAmount(), budget.getCurrency(),
                budget.getPeriod(), budget.getServiceName(), budget.filter().environmentParameter(),
                budget.getWarningThresholdPercent(), budget.isActive(), budgets.evaluate(budget, asOf));
    }
}
