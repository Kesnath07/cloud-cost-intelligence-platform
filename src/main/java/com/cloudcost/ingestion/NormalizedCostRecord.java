package com.cloudcost.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.cloudcost.costdata.ChargeType;

/** A validated, normalized cost amount ready to be stored. */
public record NormalizedCostRecord(
        LocalDate usageDate,
        String accountId,
        String serviceName,
        String region,
        String environment,
        ChargeType chargeType,
        BigDecimal amount,
        String currency,
        boolean estimated) {

    /** Fields that identify a record; mirrors the {@code uk_cost_record_natural_key} constraint. */
    public record NaturalKey(LocalDate usageDate, String accountId, String serviceName, String region,
            String environment, ChargeType chargeType) {
    }

    public NaturalKey naturalKey() {
        return new NaturalKey(usageDate, accountId, serviceName, region, environment, chargeType);
    }
}
