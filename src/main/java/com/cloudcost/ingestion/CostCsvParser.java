package com.cloudcost.ingestion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.cloudcost.config.PlatformProperties;
import com.cloudcost.costdata.ChargeType;

import org.springframework.stereotype.Component;

/**
 * Parses the documented cost CSV format (see {@code sample-data/README.md}):
 *
 * <pre>
 * usage_date,service,amount,currency[,environment][,account_id][,region][,charge_type]
 * </pre>
 *
 * Every row is validated and normalized. Duplicate rows within one file are errors, because
 * they would otherwise be silently dropped by the database's natural-key constraint.
 */
@Component
public class CostCsvParser {

    static final String USAGE_DATE = "usage_date";
    static final String SERVICE = "service";
    static final String AMOUNT = "amount";
    static final String CURRENCY = "currency";
    static final String ENVIRONMENT = "environment";
    static final String ACCOUNT_ID = "account_id";
    static final String REGION = "region";
    static final String CHARGE_TYPE = "charge_type";

    public static final Set<String> REQUIRED_COLUMNS = orderedSet(USAGE_DATE, SERVICE, AMOUNT, CURRENCY);
    public static final Set<String> OPTIONAL_COLUMNS = orderedSet(ENVIRONMENT, ACCOUNT_ID, REGION, CHARGE_TYPE);

    private final PlatformProperties platformProperties;
    private final ImportProperties importProperties;
    private final Clock clock;

    public CostCsvParser(PlatformProperties platformProperties, ImportProperties importProperties, Clock clock) {
        this.platformProperties = platformProperties;
        this.importProperties = importProperties;
        this.clock = clock;
    }

    public ParseResult<NormalizedCostRecord> parse(byte[] content) {
        LocalDate today = LocalDate.now(clock);
        List<RowError> errors = new ArrayList<>();
        List<NormalizedCostRecord> records = new ArrayList<>();
        Map<NormalizedCostRecord.NaturalKey, Long> firstRowByKey = new HashMap<>();

        int dataRows = CsvTable.read(content, REQUIRED_COLUMNS, OPTIONAL_COLUMNS, importProperties.maxRows(), errors,
                row -> {
                    FieldReader fields = new FieldReader(row, errors, today);
                    NormalizedCostRecord record = parseRow(fields, errors);
                    if (record == null) {
                        return;
                    }
                    Long firstRow = firstRowByKey.putIfAbsent(record.naturalKey(), fields.rowNumber());
                    if (firstRow != null) {
                        fields.error(null, "Duplicates row " + firstRow
                                + " (same usage date, account, service, region, environment and charge type)");
                        return;
                    }
                    if (errors.isEmpty()) {
                        records.add(record);
                    }
                });
        return ParseResult.of(records, errors, dataRows, importProperties.maxReportedErrors());
    }

    private NormalizedCostRecord parseRow(FieldReader fields, List<RowError> errors) {
        int errorsBefore = errors.size();
        LocalDate usageDate = fields.date(USAGE_DATE);
        String service = fields.serviceName(SERVICE);
        BigDecimal amount = fields.amount(AMOUNT, true);
        String currency = fields.currency(CURRENCY, platformProperties.reportingCurrency(), true);
        String environment = fields.environment(ENVIRONMENT);
        String accountId = fields.accountId(ACCOUNT_ID);
        String region = fields.region(REGION);
        ChargeType chargeType = chargeType(fields);
        if (errors.size() > errorsBefore) {
            return null;
        }
        if (!chargeType.permitsSign(amount.signum())) {
            fields.error(AMOUNT, amount.signum() < 0
                    ? "Negative amounts are only allowed for charge types CREDIT, REFUND and NET"
                    : "Charge type " + chargeType + " requires a zero or negative amount");
            return null;
        }
        return new NormalizedCostRecord(usageDate, accountId, service, region, environment, chargeType, amount,
                currency, false);
    }

    private static ChargeType chargeType(FieldReader fields) {
        String value = fields.optionalText(CHARGE_TYPE).toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return ChargeType.USAGE;
        }
        try {
            return ChargeType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            fields.error(CHARGE_TYPE, "'" + value + "' is not a valid charge type; expected one of "
                    + Arrays.toString(ChargeType.values()));
            return null;
        }
    }

    static Set<String> orderedSet(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
