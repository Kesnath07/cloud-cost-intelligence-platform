package com.cloudcost.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.cloudcost.config.PlatformProperties;
import com.cloudcost.support.TestClocks;

import org.junit.jupiter.api.Test;

class UtilizationCsvParserTest {

    private final UtilizationCsvParser parser = new UtilizationCsvParser(new PlatformProperties("USD"),
            new ImportProperties(1000, 50), TestClocks.fixed());

    private ParseResult<NormalizedUtilization> parse(String csv) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesMeasurementsWithOptionalCost() {
        ParseResult<NormalizedUtilization> result = parse("""
                resource_id,service,period_start,period_end,avg_cpu_percent,max_cpu_percent,period_cost,currency
                i-0abc,EC2,2026-09-01,2026-09-30,4.2,18.5,138.24,USD
                i-0def,EC2,2026-09-01,2026-09-30,40,80,,
                """);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0).periodCost()).isEqualByComparingTo("138.24");
        assertThat(result.records().get(1).periodCost()).isNull();
        assertThat(result.records().get(1).currency()).isNull();
    }

    @Test
    void validatesPercentagesPeriodsAndCostCurrency() {
        ParseResult<NormalizedUtilization> result = parse("""
                resource_id,service,period_start,period_end,avg_cpu_percent,max_cpu_percent,period_cost
                i-1,EC2,2026-09-01,2026-09-30,120,130,
                i-2,EC2,2026-09-30,2026-09-01,1,2,
                i-3,EC2,2026-09-01,2026-09-30,50,20,
                i-4,EC2,2026-09-01,2026-09-30,1,2,10.00
                """);

        assertThat(result.errors()).extracting(RowError::describe).containsExactlyInAnyOrder(
                "Row 2, column 'avg_cpu_percent': Percentage 120 must be between 0 and 100",
                "Row 2, column 'max_cpu_percent': Percentage 130 must be between 0 and 100",
                "Row 3, column 'period_end': period_end must be on or after period_start",
                "Row 4, column 'avg_cpu_percent': Average CPU cannot exceed maximum CPU",
                "Row 5, column 'currency': A currency code is required (USD)");
    }
}
