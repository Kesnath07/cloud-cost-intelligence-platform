package com.cloudcost.budget;

import java.math.BigDecimal;
import java.time.Instant;

import com.cloudcost.analysis.CostFilter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "cost_budget")
public class CostBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "limit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal limitAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BudgetPeriod period;

    @Column(name = "service_name", length = 128)
    private String serviceName;

    @Column(length = 64)
    private String environment;

    @Column(name = "warning_threshold_percent", nullable = false)
    private int warningThresholdPercent;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected CostBudget() {
    }

    public CostBudget(String currency, Instant now) {
        this.currency = currency;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void apply(BudgetForm form, Instant now) {
        this.name = form.getName().strip();
        this.limitAmount = form.getLimitAmount();
        this.period = form.getPeriod();
        this.serviceName = form.serviceScope();
        this.environment = form.environmentScope();
        this.warningThresholdPercent = form.getWarningThresholdPercent();
        this.active = form.isActive();
        this.updatedAt = now;
    }

    /** The cost records this budget applies to. */
    public CostFilter filter() {
        return new CostFilter(serviceName, environment);
    }

    public String scopeDescription() {
        String service = serviceName == null ? "All services" : serviceName;
        String env = environment == null ? "all environments"
                : environment.isEmpty() ? CostFilter.UNTAGGED_LABEL : environment;
        return service + ", " + env;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BudgetPeriod getPeriod() {
        return period;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getEnvironment() {
        return environment;
    }

    public int getWarningThresholdPercent() {
        return warningThresholdPercent;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
