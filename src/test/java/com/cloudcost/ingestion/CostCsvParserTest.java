package com.cloudcost.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.cloudcost.config.PlatformProperties;
import com.cloudcost.costdata.ChargeType;
import com.cloudcost.support.TestClocks;

import org.junit.jupiter.api.Test;

class CostCsvParserTest {

    private final CostCsvParser parser =
            new CostCsvParser(new PlatformProperties("USD"), new ImportProperties(1000, 50), TestClocks.fixed());

    private ParseResult<NormalizedCostRecord> parse(String csv) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesAndNormalizesValidRows() {
        ParseResult<NormalizedCostRecord> result = parse("""
                Usage_Date,Service,Amount,Currency,Environment,Account_ID,Region
                2026-09-01, AmazonEC2 ,12.3456789,usd,Prod,111122223333,US-EAST-1
                2026-09-01,Amazon   S3,0.5,USD,,,
                """);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.records()).hasSize(2);
        NormalizedCostRecord ec2 = result.records().get(0);
        assertThat(ec2.usageDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(ec2.serviceName()).isEqualTo("Amazon Elastic Compute Cloud - Compute");
        assertThat(ec2.amount()).isEqualByComparingTo("12.345679");
        assertThat(ec2.amount().scale()).isEqualTo(6);
        assertThat(ec2.currency()).isEqualTo("USD");
        assertThat(ec2.environment()).isEqualTo("production");
        assertThat(ec2.region()).isEqualTo("us-east-1");
        assertThat(ec2.chargeType()).isEqualTo(ChargeType.USAGE);

        NormalizedCostRecord s3 = result.records().get(1);
        assertThat(s3.serviceName()).isEqualTo("Amazon Simple Storage Service");
        assertThat(s3.environment()).isEmpty();
        assertThat(s3.accountId()).isEmpty();
    }

    @Test
    void acceptsByteOrderMarkAndNegativeCredits() {
        ParseResult<NormalizedCostRecord> result = parse("""
                ﻿usage_date,service,amount,currency,charge_type
                2026-09-01,AWS Lambda,-4.25,USD,CREDIT
                """);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.records().get(0).amount()).isEqualByComparingTo(new BigDecimal("-4.25"));
    }

    @Test
    void reportsEveryInvalidValueWithRowAndColumn() {
        ParseResult<NormalizedCostRecord> result = parse("""
                usage_date,service,amount,currency,region,account_id
                2026-13-01,EC2,abc,EUR,us-east,123
                """);

        assertThat(result.records()).isEmpty();
        assertThat(result.errors()).extracting(RowError::column)
                .containsExactlyInAnyOrder("usage_date", "amount", "currency", "region", "account_id");
        assertThat(result.errors()).allSatisfy(error -> assertThat(error.row()).isEqualTo(2));
        assertThat(result.errors()).extracting(RowError::describe)
                .anySatisfy(text -> assertThat(text).contains("does not match the reporting currency USD"));
    }

    @Test
    void rejectsTheWholeFileWhenAnyRowIsInvalid() {
        ParseResult<NormalizedCostRecord> result = parse("""
                usage_date,service,amount,currency
                2026-09-01,EC2,10.00,USD
                2026-09-02,EC2,$5.00,USD
                """);

        assertThat(result.records()).isEmpty();
        assertThat(result.errors()).singleElement()
                .satisfies(error -> assertThat(error.describe()).startsWith("Row 3, column 'amount'"));
    }

    @Test
    void rejectsSignsThatContradictTheChargeType() {
        ParseResult<NormalizedCostRecord> result = parse("""
                usage_date,service,amount,currency,charge_type
                2026-09-01,EC2,-1.00,USD,USAGE
                2026-09-01,EC2,1.00,USD,REFUND
                """);

        assertThat(result.errors()).hasSize(2);
    }

    @Test
    void rejectsDuplicateRowsWithinAFile() {
        ParseResult<NormalizedCostRecord> result = parse("""
                usage_date,service,amount,currency,environment
                2026-09-01,EC2,1.00,USD,prod
                2026-09-01,Amazon EC2,2.00,USD,production
                """);

        assertThat(result.errors()).singleElement()
                .satisfies(error -> assertThat(error.describe()).contains("Duplicates row 2"));
    }

    @Test
    void rejectsFutureDates() {
        ParseResult<NormalizedCostRecord> result = parse("""
                usage_date,service,amount,currency
                2026-10-11,EC2,1.00,USD
                """);

        assertThat(result.errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("is in the future"));
    }

    @Test
    void reportsMissingAndUnknownColumns() {
        ParseResult<NormalizedCostRecord> result = parse("""
                usage_date,service,amount,enviroment
                2026-09-01,EC2,1.00,prod
                """);

        assertThat(result.errors()).extracting(RowError::message).containsExactlyInAnyOrder(
                "Missing required column(s): currency",
                "Unknown column(s): enviroment. Allowed columns: usage_date, service, amount, currency, "
                        + "environment, account_id, region, charge_type");
    }

    @Test
    void rejectsEmptyFilesAndHeaderOnlyFiles() {
        assertThat(parse("").errors()).extracting(RowError::message).containsExactly("The file is empty");
        assertThat(parse("usage_date,service,amount,currency\n").errors()).extracting(RowError::message)
                .containsExactly("The file contains a header row but no data rows");
    }

    @Test
    void reportsRowsWithTheWrongNumberOfValues() {
        ParseResult<NormalizedCostRecord> result = parse("""
                usage_date,service,amount,currency
                2026-09-01,EC2,1.00
                """);

        assertThat(result.errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).isEqualTo("Expected 4 values but found 3"));
    }

    @Test
    void limitsTheNumberOfReportedErrors() {
        CostCsvParser strict = new CostCsvParser(new PlatformProperties("USD"), new ImportProperties(1000, 2),
                TestClocks.fixed());
        String csv = "usage_date,service,amount,currency\n" + "bad,EC2,1,USD\n".repeat(5);

        ParseResult<NormalizedCostRecord> result = strict.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.errors()).hasSize(2);
        assertThat(result.errorsTruncated()).isTrue();
        assertThat(result.dataRows()).isEqualTo(5);
    }

    @Test
    void rejectsFilesWithMoreRowsThanAllowed() {
        CostCsvParser limited = new CostCsvParser(new PlatformProperties("USD"), new ImportProperties(2, 10),
                TestClocks.fixed());
        String csv = "usage_date,service,amount,currency\n" + "2026-09-01,EC2,1,USD\n".repeat(3);

        ParseResult<NormalizedCostRecord> result = limited.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.records()).isEmpty();
        assertThat(result.errors()).extracting(RowError::message)
                .anySatisfy(message -> assertThat(message).contains("more than 2 data rows"));
    }
}
