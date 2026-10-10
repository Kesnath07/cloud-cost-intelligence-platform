package com.cloudcost.support;

import java.nio.charset.StandardCharsets;

import com.cloudcost.costdata.CostSource;
import com.cloudcost.ingestion.CostIngestionService;
import com.cloudcost.ingestion.ImportOutcome;

/** Imports small, hand-written cost datasets through the real ingestion path. */
public final class CostDataFixture {

    public static final String HEADER = "usage_date,service,amount,currency,environment,charge_type\n";

    private CostDataFixture() {
    }

    public static ImportOutcome importRows(CostIngestionService ingestion, String rows) {
        ImportOutcome outcome = ingestion.importCostFile(CostSource.CSV_UPLOAD, "fixture.csv",
                (HEADER + rows).getBytes(StandardCharsets.UTF_8));
        if (!outcome.succeeded()) {
            throw new IllegalStateException("Fixture rejected: " + outcome.errors());
        }
        return outcome;
    }
}
