package com.cloudcost.web;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.cloudcost.analysis.BreakdownItem;
import com.cloudcost.common.MoneyMath;

/** Presentation helpers for breakdown lists. */
final class Breakdowns {

    static final String OTHER = "All other services";

    private Breakdowns() {
    }

    /** The {@code limit} largest items plus one item summing the rest, so long tails stay readable. */
    static List<BreakdownItem> topWithOther(List<BreakdownItem> items, int limit, BigDecimal total) {
        if (items.size() <= limit + 1) {
            return items;
        }
        List<BreakdownItem> result = new ArrayList<>(items.subList(0, limit));
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal previous = BigDecimal.ZERO;
        for (BreakdownItem item : items.subList(limit, items.size())) {
            amount = amount.add(item.amount());
            previous = previous.add(item.previousAmount());
        }
        result.add(new BreakdownItem(OTHER, amount, previous, amount.subtract(previous),
                MoneyMath.percentChange(amount, previous), MoneyMath.share(amount, total)));
        return result;
    }
}
