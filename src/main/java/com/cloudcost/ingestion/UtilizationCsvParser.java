package com.cloudcost.ingestion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cloudcost.config.PlatformProperties;

import org.springframework.stereotype.Component;

/**
 * Parses resource utilization measurements:
 *
 * <pre>
 * resource_id,service,period_start,period_end,avg_cpu_percent,max_cpu_percent
 *     [,resource_type][,environment][,region][,period_cost][,currency]
 * </pre>
 */
@Component
public class UtilizationCsvParser {

    static final String RESOURCE_ID = "resource_id";
    static final String RESOURCE_TYPE = "resource_type";
    static final String SERVICE = "service";
    static final String ENVIRONMENT = "environment";
    static final String REGION = "region";
    static final String PERIOD_START = "period_start";
    static final String PERIOD_END = "period_end";
    static final String AVG_CPU = "avg_cpu_percent";
    static final String MAX_CPU = "max_cpu_percent";
    static final String PERIOD_COST = "period_cost";
    static final String CURRENCY = "currency";

    public static final Set<String> REQUIRED_COLUMNS =
            CostCsvParser.orderedSet(RESOURCE_ID, SERVICE, PERIOD_START, PERIOD_END, AVG_CPU, MAX_CPU);
    public static final Set<String> OPTIONAL_COLUMNS =
            CostCsvParser.orderedSet(RESOURCE_TYPE, ENVIRONMENT, REGION, PERIOD_COST, CURRENCY);

    private record Key(String resourceId, LocalDate periodStart, LocalDate periodEnd) {
    }

    private final PlatformProperties platformProperties;
    private final ImportProperties importProperties;
    private final Clock clock;

    public UtilizationCsvParser(PlatformProperties platformProperties, ImportProperties importProperties,
            Clock clock) {
        this.platformProperties = platformProperties;
        this.importProperties = importProperties;
        this.clock = clock;
    }

    public ParseResult<NormalizedUtilization> parse(byte[] content) {
        LocalDate today = LocalDate.now(clock);
        List<RowError> errors = new ArrayList<>();
        List<NormalizedUtilization> records = new ArrayList<>();
        Map<Key, Long> firstRowByKey = new HashMap<>();

        int dataRows = CsvTable.read(content, REQUIRED_COLUMNS, OPTIONAL_COLUMNS, importProperties.maxRows(), errors,
                row -> {
                    FieldReader fields = new FieldReader(row, errors, today);
                    NormalizedUtilization record = parseRow(fields, errors);
                    if (record == null) {
                        return;
                    }
                    Key key = new Key(record.resourceId(), record.periodStart(), record.periodEnd());
                    Long firstRow = firstRowByKey.putIfAbsent(key, fields.rowNumber());
                    if (firstRow != null) {
                        fields.error(null, "Duplicates row " + firstRow + " (same resource and period)");
                        return;
                    }
                    if (errors.isEmpty()) {
                        records.add(record);
                    }
                });
        return ParseResult.of(records, errors, dataRows, importProperties.maxReportedErrors());
    }

    private NormalizedUtilization parseRow(FieldReader fields, List<RowError> errors) {
        int errorsBefore = errors.size();
        String resourceId = fields.requiredText(RESOURCE_ID, 256);
        String resourceType = fields.optionalText(RESOURCE_TYPE);
        String service = fields.serviceName(SERVICE);
        String environment = fields.environment(ENVIRONMENT);
        String region = fields.region(REGION);
        LocalDate periodStart = fields.date(PERIOD_START);
        LocalDate periodEnd = fields.date(PERIOD_END);
        BigDecimal avgCpu = fields.percentage(AVG_CPU);
        BigDecimal maxCpu = fields.percentage(MAX_CPU);
        BigDecimal periodCost = fields.amount(PERIOD_COST, false);
        String currency = fields.currency(CURRENCY, platformProperties.reportingCurrency(), periodCost != null);
        if (resourceType.length() > 64) {
            fields.error(RESOURCE_TYPE, "Must be at most 64 characters");
        }
        if (errors.size() > errorsBefore) {
            return null;
        }
        if (periodEnd.isBefore(periodStart)) {
            fields.error(PERIOD_END, "period_end must be on or after period_start");
            return null;
        }
        if (avgCpu.compareTo(maxCpu) > 0) {
            fields.error(AVG_CPU, "Average CPU cannot exceed maximum CPU");
            return null;
        }
        if (periodCost != null && periodCost.signum() < 0) {
            fields.error(PERIOD_COST, "period_cost cannot be negative");
            return null;
        }
        return new NormalizedUtilization(resourceId, resourceType, service, environment, region, periodStart,
                periodEnd, avgCpu, maxCpu, periodCost, periodCost == null ? null : currency);
    }
}
