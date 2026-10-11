package com.cloudcost.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.cloudcost.common.DateRange;
import com.cloudcost.common.InvalidRequestException;
import com.cloudcost.ingestion.CostIngestionService;
import com.cloudcost.support.CostDataFixture;
import com.cloudcost.support.DatabaseCleaner;
import com.cloudcost.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class CostAnalysisServiceIntegrationTest {

    private static final DateRange SEP_3_TO_4 = DateRange.of(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4));

    @Autowired
    private CostAnalysisService analysis;

    @Autowired
    private CostIngestionService ingestion;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    private void importTwoPeriods() {
        // Previous period: Sep 1-2. Current period: Sep 3-4.
        CostDataFixture.importRows(ingestion, """
                2026-09-01,EC2,100.00,USD,production,USAGE
                2026-09-01,S3,10.00,USD,,USAGE
                2026-09-02,EC2,100.00,USD,production,USAGE
                2026-09-02,RDS,40.00,USD,staging,USAGE
                2026-09-03,EC2,150.00,USD,production,USAGE
                2026-09-03,S3,10.00,USD,,USAGE
                2026-09-03,S3,-2.50,USD,,REFUND
                2026-09-04,EC2,130.00,USD,production,USAGE
                2026-09-04,Lambda,12.345678,USD,development,USAGE
                """);
    }

    @Test
    void comparesTotalsWithThePreviousPeriodOfEqualLength() {
        importTwoPeriods();

        CostSummary summary = analysis.summarize(SEP_3_TO_4, CostFilter.NONE);

        assertThat(summary.previousRange()).isEqualTo(DateRange.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)));
        assertThat(summary.total()).isEqualByComparingTo("299.845678");
        assertThat(summary.previousTotal()).isEqualByComparingTo("250.00");
        assertThat(summary.change()).isEqualByComparingTo("49.845678");
        assertThat(summary.changePercent()).isEqualByComparingTo("19.94");
        assertThat(summary.averageDailyCost()).isEqualByComparingTo("149.922839");
        assertThat(summary.daysWithData()).isEqualTo(2);
        assertThat(summary.hasGaps()).isFalse();
    }

    @Test
    void breaksDownByServiceWithChangesSharesAndServicesThatDisappeared() {
        importTwoPeriods();

        CostSummary summary = analysis.summarize(SEP_3_TO_4, CostFilter.NONE);

        assertThat(summary.byService()).extracting(BreakdownItem::name).containsExactly(
                "Amazon Elastic Compute Cloud - Compute", "AWS Lambda", "Amazon Simple Storage Service",
                "Amazon Relational Database Service");
        BreakdownItem ec2 = summary.byService().get(0);
        assertThat(ec2.amount()).isEqualByComparingTo("280");
        assertThat(ec2.changePercent()).isEqualByComparingTo("40.00");
        assertThat(ec2.sharePercent()).isEqualByComparingTo("93.38");

        BreakdownItem lambda = summary.byService().get(1);
        assertThat(lambda.previousAmount()).isEqualByComparingTo("0");
        assertThat(lambda.changePercent()).as("no baseline").isNull();

        BreakdownItem s3 = summary.byService().get(2);
        assertThat(s3.amount()).as("refund is netted").isEqualByComparingTo("7.50");
        assertThat(s3.changePercent()).isEqualByComparingTo("-25.00");

        BreakdownItem rds = summary.byService().get(3);
        assertThat(rds.amount()).isEqualByComparingTo("0");
        assertThat(rds.change()).isEqualByComparingTo("-40");

        assertThat(summary.largestIncreases()).extracting(BreakdownItem::name)
                .containsExactly("Amazon Elastic Compute Cloud - Compute", "AWS Lambda");
    }

    @Test
    void breaksDownByEnvironmentIncludingUntaggedCosts() {
        importTwoPeriods();

        CostSummary summary = analysis.summarize(SEP_3_TO_4, CostFilter.NONE);

        assertThat(summary.byEnvironment()).extracting(BreakdownItem::name, BreakdownItem::amount)
                .containsExactly(
                        tuple("production", new BigDecimal("280.000000")),
                        tuple("development", new BigDecimal("12.345678")),
                        tuple("", new BigDecimal("7.500000")),
                        tuple("staging", new BigDecimal("0")));
    }

    @Test
    void filtersByServiceAndByUntaggedEnvironment() {
        importTwoPeriods();

        CostSummary ec2 = analysis.summarize(SEP_3_TO_4, CostFilter.fromRequest("Amazon Elastic Compute Cloud - Compute", null));
        CostSummary untagged = analysis.summarize(SEP_3_TO_4, CostFilter.fromRequest(null, "(untagged)"));

        assertThat(ec2.total()).isEqualByComparingTo("280");
        assertThat(ec2.previousTotal()).isEqualByComparingTo("200");
        assertThat(untagged.total()).isEqualByComparingTo("7.50");
        assertThat(untagged.byService()).extracting(BreakdownItem::name)
                .containsExactly("Amazon Simple Storage Service");
    }

    @Test
    void distinguishesDataGapsFromZeroCostDays() {
        CostDataFixture.importRows(ingestion, """
                2026-09-01,EC2,10.00,USD,production,USAGE
                2026-09-03,S3,5.00,USD,production,USAGE
                """);

        CostSummary summary = analysis.summarize(DateRange.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)),
                CostFilter.service("Amazon Simple Storage Service"));

        assertThat(summary.daily()).extracting(DailyCost::hasData).containsExactly(true, false, true);
        assertThat(summary.daily().get(0).amount()).as("data exists, but none for S3").isEqualByComparingTo("0");
        assertThat(summary.daily().get(1).amount()).as("no data at all").isNull();
        assertThat(summary.hasGaps()).isTrue();
    }

    @Test
    void handlesAZeroBaselineWithoutInventingAPercentage() {
        CostDataFixture.importRows(ingestion, "2026-09-03,EC2,10.00,USD,production,USAGE\n");

        CostSummary summary = analysis.summarize(SEP_3_TO_4, CostFilter.NONE);

        assertThat(summary.previousTotal()).isEqualByComparingTo("0");
        assertThat(summary.changePercent()).isNull();
        assertThat(summary.hasPreviousData()).isFalse();
    }

    @Test
    void emptyDatabaseProducesAnEmptySummary() {
        CostSummary summary = analysis.summarize(SEP_3_TO_4, CostFilter.NONE);

        assertThat(summary.hasData()).isFalse();
        assertThat(summary.total()).isEqualByComparingTo("0");
        assertThat(summary.averageDailyCost()).isNull();
        assertThat(summary.byService()).isEmpty();
        assertThat(summary.daily()).allMatch(day -> !day.hasData() && day.amount() == null);
        assertThat(analysis.coverage().isEmpty()).isTrue();
    }

    @Test
    void defaultsToThirtyDaysEndingOnTheLatestStoredData() {
        importTwoPeriods();

        assertThat(analysis.resolveRange(null, null))
                .isEqualTo(DateRange.of(LocalDate.of(2026, 8, 6), LocalDate.of(2026, 9, 4)));
        assertThatThrownBy(() -> analysis.resolveRange(LocalDate.of(2026, 9, 1), null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> analysis.resolveRange(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void groupsMonthlyTotalsAndFlagsPartialMonths() {
        CostDataFixture.importRows(ingestion, """
                2026-07-31,EC2,5.00,USD,production,USAGE
                2026-08-01,EC2,10.00,USD,production,USAGE
                2026-08-31,EC2,20.00,USD,production,USAGE
                2026-09-01,EC2,7.00,USD,production,USAGE
                """);

        List<MonthlyCost> months = analysis.monthlyTrend(LocalDate.of(2026, 9, 1), 3, CostFilter.NONE);

        assertThat(months).extracting(MonthlyCost::month)
                .containsExactly(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9));
        assertThat(months.get(1).amount()).isEqualByComparingTo("30");
        assertThat(months.get(1).partial()).isFalse();
        assertThat(months.get(1).daysWithData()).isEqualTo(2);
        assertThat(months.get(2).partial()).isTrue();
    }

    @Test
    void producesZeroFilledDailySeriesPerDimension() {
        importTwoPeriods();

        var series = analysis.dailySeriesBy(CostDimension.SERVICE,
                DateRange.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4)), CostFilter.NONE);

        assertThat(series.get("AWS Lambda")).hasSize(4);
        assertThat(series.get("AWS Lambda").get(LocalDate.of(2026, 9, 1))).isEqualByComparingTo("0");
        assertThat(series.get("AWS Lambda").get(LocalDate.of(2026, 9, 4))).isEqualByComparingTo("12.345678");
    }

    @Test
    void buildsChartReadySeriesPerServiceOrderedByTotalWithGapsAsNull() {
        importTwoPeriods();
        // Sep 5 has no data at all and must be reported as a gap, not as zero spend.
        DateRange range = DateRange.of(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 5));

        DailyCostSeries daily = analysis.dailyCostSeries(range, CostFilter.NONE, CostDimension.SERVICE);

        assertThat(daily.dates()).containsExactly(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4),
                LocalDate.of(2026, 9, 5));
        assertThat(daily.series()).extracting(DailyCostSeries.Series::name).containsExactly(
                "Amazon Elastic Compute Cloud - Compute", "AWS Lambda", "Amazon Simple Storage Service");
        DailyCostSeries.Series lambda = daily.series().get(1);
        assertThat(lambda.total()).isEqualByComparingTo("12.345678");
        assertThat(lambda.amounts().get(0)).isEqualByComparingTo("0");
        assertThat(lambda.amounts().get(1)).isEqualByComparingTo("12.345678");
        assertThat(lambda.amounts().get(2)).isNull();
        assertThat(daily.series().get(2).total()).isEqualByComparingTo("7.50");
    }

    @Test
    void buildsASingleFilteredTotalSeriesWithoutGrouping() {
        importTwoPeriods();

        DailyCostSeries daily = analysis.dailyCostSeries(SEP_3_TO_4, CostFilter.environment("production"), null);

        assertThat(daily.groupBy()).isNull();
        assertThat(daily.series()).singleElement().satisfies(total -> {
            assertThat(total.name()).isEqualTo(DailyCostSeries.TOTAL);
            assertThat(total.total()).isEqualByComparingTo("280.00");
            assertThat(total.amounts()).hasSize(2);
        });
    }

    @Test
    void parsesDimensionParametersIgnoringCase() {
        assertThat(CostDimension.fromParameter("service")).isEqualTo(CostDimension.SERVICE);
        assertThat(CostDimension.fromParameter(" Environment ")).isEqualTo(CostDimension.ENVIRONMENT);
        assertThatThrownBy(() -> CostDimension.fromParameter("region"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("service, environment");
    }
}
