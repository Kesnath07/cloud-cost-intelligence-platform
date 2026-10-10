package com.cloudcost.costdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * One normalized daily cost amount. Rows are written in bulk by {@code CostRecordWriter}
 * (plain JDBC with PostgreSQL {@code ON CONFLICT} handling) and are read-only through JPA.
 */
@Entity
@Immutable
@Table(name = "cost_record")
public class CostRecord {

    @Id
    private Long id;

    @Column(name = "import_job_id", nullable = false)
    private Long importJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CostSource source;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "account_id", nullable = false, length = 12)
    private String accountId;

    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    @Column(nullable = false, length = 32)
    private String region;

    @Column(nullable = false, length = 64)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_type", nullable = false, length = 16)
    private ChargeType chargeType;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean estimated;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected CostRecord() {
    }

    public Long getId() {
        return id;
    }

    public Long getImportJobId() {
        return importJobId;
    }

    public CostSource getSource() {
        return source;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getRegion() {
        return region;
    }

    public String getEnvironment() {
        return environment;
    }

    public ChargeType getChargeType() {
        return chargeType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isEstimated() {
        return estimated;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
