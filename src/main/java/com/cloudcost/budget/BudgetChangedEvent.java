package com.cloudcost.budget;

/** Published when a budget is created, updated or deleted. */
public record BudgetChangedEvent(long budgetId) {
}
