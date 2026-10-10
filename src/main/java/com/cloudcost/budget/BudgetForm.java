package com.cloudcost.budget;

import java.math.BigDecimal;

import com.cloudcost.analysis.CostFilter;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Input for creating or updating a budget, used by both the HTML form and the JSON API.
 * A blank service or environment means "all"; the environment {@code (untagged)} selects
 * costs without an environment tag.
 */
public class BudgetForm {

    @NotBlank(message = "Enter a name")
    @Size(max = 100, message = "Use at most 100 characters")
    private String name;

    @NotNull(message = "Enter a spending limit")
    @DecimalMin(value = "0.01", message = "The limit must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "Use at most two decimal places")
    private BigDecimal limitAmount;

    @NotNull(message = "Choose a period")
    private BudgetPeriod period = BudgetPeriod.MONTHLY;

    @Size(max = 128, message = "Use at most 128 characters")
    private String service;

    @Size(max = 64, message = "Use at most 64 characters")
    private String environment;

    @NotNull(message = "Enter a warning threshold")
    @Min(value = 1, message = "The warning threshold must be between 1 and 100")
    @Max(value = 100, message = "The warning threshold must be between 1 and 100")
    private Integer warningThresholdPercent = 80;

    private boolean active = true;

    public static BudgetForm from(CostBudget budget) {
        BudgetForm form = new BudgetForm();
        form.name = budget.getName();
        form.limitAmount = budget.getLimitAmount();
        form.period = budget.getPeriod();
        form.service = budget.getServiceName();
        form.environment = budget.filter().environmentParameter();
        form.warningThresholdPercent = budget.getWarningThresholdPercent();
        form.active = budget.isActive();
        return form;
    }

    String serviceScope() {
        return service == null || service.isBlank() ? null : service.strip();
    }

    String environmentScope() {
        return CostFilter.fromRequest(null, environment).environment();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }

    public BudgetPeriod getPeriod() {
        return period;
    }

    public void setPeriod(BudgetPeriod period) {
        this.period = period;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Integer getWarningThresholdPercent() {
        return warningThresholdPercent;
    }

    public void setWarningThresholdPercent(Integer warningThresholdPercent) {
        this.warningThresholdPercent = warningThresholdPercent;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
