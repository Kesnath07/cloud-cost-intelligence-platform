package com.cloudcost.analysis;

import java.time.LocalDate;

/** The span of stored cost data; both dates are {@code null} when nothing is stored. */
public record DataCoverage(LocalDate firstDate, LocalDate lastDate, long records) {

    public boolean isEmpty() {
        return records == 0;
    }
}
