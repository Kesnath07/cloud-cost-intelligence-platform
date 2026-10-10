package com.cloudcost.costdata;

import org.springframework.stereotype.Component;

/**
 * Exposed to templates as {@code @demoDataStatus} so that every page can warn when the
 * fictional demonstration dataset is part of the displayed figures.
 */
@Component("demoDataStatus")
public class DemoDataStatus {

    private final CostRecordRepository records;

    public DemoDataStatus(CostRecordRepository records) {
        this.records = records;
    }

    public boolean present() {
        return records.existsBySource(CostSource.DEMO_SAMPLE);
    }
}
