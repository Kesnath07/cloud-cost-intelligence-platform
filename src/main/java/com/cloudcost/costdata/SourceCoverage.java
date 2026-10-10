package com.cloudcost.costdata;

import java.time.LocalDate;

/** How much cost data one source has contributed and which dates it covers. */
public record SourceCoverage(CostSource source, long records, LocalDate firstDate, LocalDate lastDate) {
}
