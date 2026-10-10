package com.cloudcost.budget;

import java.math.BigDecimal;

import com.cloudcost.common.MoneyMath;

/**
 * Deterministic budget status rules:
 * <ul>
 *   <li>EXCEEDED when spend &ge; limit</li>
 *   <li>WARNING when spend &times; 100 &ge; limit &times; warning threshold. The comparison is exact;
 *       the rounded utilization percentage is for display only, so 79.998% never counts as 80%</li>
 *   <li>NORMAL otherwise, including net-negative spend caused by credits</li>
 * </ul>
 */
public final class BudgetEvaluator {

    private BudgetEvaluator() {
    }

    public static BigDecimal utilizationPercent(BigDecimal spend, BigDecimal limit) {
        requirePositive(limit);
        return MoneyMath.percentOf(spend, limit);
    }

    public static BudgetStatus status(BigDecimal spend, BigDecimal limit, int warningThresholdPercent) {
        requirePositive(limit);
        if (warningThresholdPercent < 1 || warningThresholdPercent > 100) {
            throw new IllegalArgumentException("Warning threshold must be between 1 and 100");
        }
        if (spend.compareTo(limit) >= 0) {
            return BudgetStatus.EXCEEDED;
        }
        BigDecimal warningLevel = limit.multiply(BigDecimal.valueOf(warningThresholdPercent));
        if (spend.multiply(BigDecimal.valueOf(100)).compareTo(warningLevel) >= 0) {
            return BudgetStatus.WARNING;
        }
        return BudgetStatus.NORMAL;
    }

    private static void requirePositive(BigDecimal limit) {
        if (limit == null || limit.signum() <= 0) {
            throw new IllegalArgumentException("Budget limit must be positive");
        }
    }
}
