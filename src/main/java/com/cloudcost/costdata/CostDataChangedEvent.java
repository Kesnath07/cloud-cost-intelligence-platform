package com.cloudcost.costdata;

import com.cloudcost.common.DateRange;

/**
 * Published after cost data for {@code affectedRange} was added, replaced or removed and the
 * change has been committed. Derived analyses (anomalies, recommendations) listen for it.
 */
public record CostDataChangedEvent(DateRange affectedRange) {
}
