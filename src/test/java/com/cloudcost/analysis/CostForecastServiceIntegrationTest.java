package com.cloudcost.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import com.cloudcost.ingestion.CostIngestionService;
import com.cloudcost.support.CostDataFixture;
import com.cloudcost.support.DatabaseCleaner;
import com.cloudcost.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class CostForecastServiceIntegrationTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Autowired
    private CostForecastService forecasts;

    @Autowired
    private CostIngestionService ingestion;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    private void importAugustAndTenSeptemberDays() {
        // August total 300.00; September 1-7 at 10.00/day, then 20.00/day on September 8-10.
        StringBuilder rows = new StringBuilder("2026-08-15,EC2,300.00,USD,production,USAGE\n");
        for (int day = 1; day <= 10; day++) {
            rows.append(String.format("2026-09-%02d,EC2,%s,USD,production,USAGE%n", day, day <= 7 ? "10.00" : "20.00"));
        }
        rows.append("2026-09-10,S3,5.00,USD,,USAGE\n");
        CostDataFixture.importRows(ingestion, rows.toString());
    }

    @Test
    void projectsTheMonthFromMonthToDateSpend() {
        importAugustAndTenSeptemberDays();

        MonthlyForecast forecast = forecasts.forecast(SEPTEMBER, CostFilter.environment("production"));

        assertThat(forecast.asOf()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(forecast.daysElapsed()).isEqualTo(10);
        assertThat(forecast.daysInMonth()).isEqualTo(30);
        assertThat(forecast.daysWithData()).isEqualTo(10);
        assertThat(forecast.complete()).isFalse();
        assertThat(forecast.monthToDate()).isEqualByComparingTo("130.00");
        assertThat(forecast.linearProjection()).isEqualByComparingTo("390.00");
        // Recent window September 4-10: 4 × 10.00 + 3 × 20.00 = 100.00 over 7 days.
        assertThat(forecast.recentDailyAverage()).isEqualByComparingTo("14.285714");
        assertThat(forecast.recentRateProjection()).isEqualByComparingTo("415.71");
        assertThat(forecast.previousMonthTotal()).isEqualByComparingTo("300.00");
        assertThat(forecast.projectedChangePercent()).isEqualByComparingTo("30.00");
    }

    @Test
    void reportsTheActualTotalWithoutProjectionsForACompleteMonth() {
        importAugustAndTenSeptemberDays();

        MonthlyForecast forecast = forecasts.forecast(YearMonth.of(2026, 8), CostFilter.NONE);

        assertThat(forecast.complete()).isTrue();
        assertThat(forecast.asOf()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(forecast.monthToDate()).isEqualByComparingTo("300.00");
        assertThat(forecast.linearProjection()).isNull();
        assertThat(forecast.recentRateProjection()).isNull();
        assertThat(forecast.projectedChangePercent()).as("no July baseline").isNull();
    }

    @Test
    void withholdsProjectionsDuringTheFirstDaysOfTheMonth() {
        CostDataFixture.importRows(ingestion, """
                2026-09-01,EC2,10.00,USD,production,USAGE
                2026-09-02,EC2,10.00,USD,production,USAGE
                """);

        MonthlyForecast forecast = forecasts.forecast(SEPTEMBER, CostFilter.NONE);

        assertThat(forecast.monthToDate()).isEqualByComparingTo("20.00");
        assertThat(forecast.recentDailyAverage()).isEqualByComparingTo("10.00");
        assertThat(forecast.linearProjection()).isNull();
        assertThat(forecast.recentRateProjection()).isNull();
    }

    @Test
    void returnsAnEmptyForecastForAMonthWithoutData() {
        importAugustAndTenSeptemberDays();

        MonthlyForecast forecast = forecasts.forecast(YearMonth.of(2026, 10), CostFilter.NONE);

        assertThat(forecast.asOf()).isNull();
        assertThat(forecast.daysElapsed()).isZero();
        assertThat(forecast.monthToDate()).isEqualByComparingTo("0");
        assertThat(forecast.linearProjection()).isNull();
        assertThat(forecast.previousMonthTotal()).isEqualByComparingTo("135.00");
    }

    @Test
    void defaultsToTheMonthOfTheLatestData() {
        importAugustAndTenSeptemberDays();

        assertThat(forecasts.defaultMonth()).isEqualTo(SEPTEMBER);
    }
}
