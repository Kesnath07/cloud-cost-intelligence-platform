package com.cloudcost.costdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Measured CPU utilization of one resource over a period, supplied by the user (for
 * example exported from CloudWatch). Written in bulk via JDBC and read-only through JPA.
 */
@Entity
@Immutable
@Table(name = "resource_utilization")
public class ResourceUtilization {

    @Id
    private Long id;

    @Column(name = "import_job_id", nullable = false)
    private Long importJobId;

    @Column(name = "resource_id", nullable = false, length = 256)
    private String resourceId;

    @Column(name = "resource_type", nullable = false, length = 64)
    private String resourceType;

    @Column(name = "service_name", nullable = false, length = 128)
    private String serviceName;

    @Column(nullable = false, length = 64)
    private String environment;

    @Column(nullable = false, length = 32)
    private String region;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "avg_cpu_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal avgCpuPercent;

    @Column(name = "max_cpu_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxCpuPercent;

    @Column(name = "period_cost", precision = 19, scale = 6)
    private BigDecimal periodCost;

    @Column(length = 3)
    private String currency;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected ResourceUtilization() {
    }

    public long periodDays() {
        return ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
    }

    public Long getId() {
        return id;
    }

    public Long getImportJobId() {
        return importJobId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getRegion() {
        return region;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getAvgCpuPercent() {
        return avgCpuPercent;
    }

    public BigDecimal getMaxCpuPercent() {
        return maxCpuPercent;
    }

    public BigDecimal getPeriodCost() {
        return periodCost;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
