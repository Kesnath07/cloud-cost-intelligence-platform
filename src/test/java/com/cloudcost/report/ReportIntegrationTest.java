package com.cloudcost.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;

import com.cloudcost.analysis.CostFilter;
import com.cloudcost.common.DateRange;
import com.cloudcost.ingestion.CostIngestionService;
import com.cloudcost.support.CostDataFixture;
import com.cloudcost.support.DatabaseCleaner;
import com.cloudcost.support.PostgresIntegrationTest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class ReportIntegrationTest {

    private static final DateRange SEPTEMBER = DateRange.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    @Autowired
    private CostReportService reports;

    @Autowired
    private CostIngestionService ingestion;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    private static List<CSVRecord> parse(String csv) throws IOException {
        return CSVFormat.RFC4180.parse(new StringReader(csv)).getRecords();
    }

    private static List<CSVRecord> section(List<CSVRecord> rows, String section) {
        return rows.stream().filter(row -> row.get(0).equals(section)).toList();
    }

    @Test
    void summaryCsvContainsEverySectionInOneConsistentTable() throws IOException {
        ingestion.loadDemoDataset();

        List<CSVRecord> rows = parse(reports.summaryCsv(reports.build(SEPTEMBER, CostFilter.NONE)));

        assertThat(rows.get(0).toList()).containsExactly(CostReportService.SUMMARY_COLUMNS);
        assertThat(rows).allSatisfy(row -> assertThat(row.size()).isEqualTo(10));
        assertThat(section(rows, "report")).anySatisfy(row -> {
            assertThat(row.get(1)).isEqualTo("data_notice");
            assertThat(row.get(9)).contains("fictional demonstration data");
        });
        assertThat(section(rows, "report")).anySatisfy(row ->
                assertThat(row.get(9)).isEqualTo("2026-09-01 to 2026-09-30"));
        assertThat(section(rows, "summary")).singleElement().satisfies(row -> {
            assertThat(row.get(2)).isEqualTo("8780.12");
            assertThat(row.get(3)).isEqualTo("7893.58");
        });
        assertThat(section(rows, "service")).hasSize(13);
        assertThat(section(rows, "environment")).extracting(row -> row.get(1)).contains("(untagged)", "production");
        assertThat(section(rows, "anomaly")).hasSize(4);
        assertThat(section(rows, "recommendation")).isNotEmpty()
                .anySatisfy(row -> assertThat(row.get(2)).isEqualTo("60.74"));
    }

    @Test
    void summaryCsvOfAnEmptyPeriodHasOnlyMetadataAndAZeroTotal() throws IOException {
        List<CSVRecord> rows = parse(reports.summaryCsv(reports.build(SEPTEMBER, CostFilter.NONE)));

        assertThat(section(rows, "summary")).singleElement().satisfies(row -> {
            assertThat(row.get(2)).isEqualTo("0.00");
            assertThat(row.get(5)).as("undefined percentage").isEmpty();
        });
        assertThat(section(rows, "service")).isEmpty();
    }

    @Test
    void dailyCsvListsCostsPerDayServiceAndEnvironment() throws IOException {
        CostDataFixture.importRows(ingestion, """
                2026-09-01,EC2,10.00,USD,production,USAGE
                2026-09-01,S3,-1.50,USD,,REFUND
                """);

        List<CSVRecord> rows = parse(reports.dailyCostsCsv(SEPTEMBER, CostFilter.NONE));

        assertThat(rows).extracting(CSVRecord::toList).containsExactly(
                List.of("date", "service", "environment", "amount", "currency"),
                List.of("2026-09-01", "Amazon Elastic Compute Cloud - Compute", "production", "10.00", "USD"),
                List.of("2026-09-01", "Amazon Simple Storage Service", "(untagged)", "-1.50", "USD"));
    }

    @Test
    void summaryCsvWithAFilterScopesTheCostFiguresAndRecordsTheFilter() throws IOException {
        CostDataFixture.importRows(ingestion, """
                2026-09-01,EC2,10.00,USD,production,USAGE
                2026-09-01,EC2,4.00,USD,,USAGE
                2026-09-02,Lambda,3.00,USD,production,USAGE
                """);

        List<CSVRecord> rows = parse(reports.summaryCsv(reports.build(SEPTEMBER,
                CostFilter.fromRequest(null, "production"))));

        assertThat(section(rows, "report")).anySatisfy(row -> {
            assertThat(row.get(1)).isEqualTo("filter");
            assertThat(row.get(9)).startsWith("environment: production;");
        });
        assertThat(section(rows, "summary")).singleElement()
                .satisfies(row -> assertThat(row.get(2)).isEqualTo("13.00"));
        assertThat(section(rows, "environment")).extracting(row -> row.get(1)).containsExactly("production");
    }

    @Test
    void summaryCsvWithoutAFilterHasNoFilterRow() throws IOException {
        List<CSVRecord> rows = parse(reports.summaryCsv(reports.build(SEPTEMBER, CostFilter.NONE)));

        assertThat(section(rows, "report")).noneSatisfy(row -> assertThat(row.get(1)).isEqualTo("filter"));
    }

    @Test
    void dailyCsvHonorsServiceAndUntaggedEnvironmentFilters() throws IOException {
        CostDataFixture.importRows(ingestion, """
                2026-09-01,EC2,10.00,USD,production,USAGE
                2026-09-01,EC2,4.00,USD,,USAGE
                2026-09-01,S3,-1.50,USD,,REFUND
                """);

        List<CSVRecord> rows = parse(reports.dailyCostsCsv(SEPTEMBER, CostFilter.fromRequest("Amazon Elastic Compute "
                + "Cloud - Compute", "(untagged)")));

        assertThat(rows).extracting(CSVRecord::toList).containsExactly(
                List.of("date", "service", "environment", "amount", "currency"),
                List.of("2026-09-01", "Amazon Elastic Compute Cloud - Compute", "(untagged)", "4.00", "USD"));
    }

    @Test
    void describesFiltersInRequestParameterForm() {
        assertThat(CostReportService.describe(CostFilter.fromRequest("AWS Lambda", "(untagged)")))
                .isEqualTo("service: AWS Lambda; environment: (untagged)");
        assertThat(CostReportService.describe(CostFilter.service("AWS Lambda"))).isEqualTo("service: AWS Lambda");
    }

    @Test
    void reportsPageCarriesTheFilterIntoTheDownloadLinks() throws Exception {
        mvc.perform(get("/reports").param("start", "2026-09-01").param("end", "2026-09-30")
                        .param("environment", "(untagged)").with(user("test-admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(
                        "/reports/daily-costs.csv?start=2026-09-01&amp;end=2026-09-30&amp;service=&amp;environment=(untagged)")));
    }

    @Test
    void downloadsAreServedAsCsvAttachments() throws Exception {
        mvc.perform(get("/api/reports/cost-summary.csv").param("start", "2026-09-01").param("end", "2026-09-30")
                        .with(httpBasic("test-admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"cost-summary-2026-09-01-to-2026-09-30.csv\""));
        mvc.perform(get("/api/reports/daily-costs.csv").param("start", "2026-09-01").param("end", "2026-09-30")
                        .param("service", "AWS Lambda").with(httpBasic("test-admin", "test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reports/daily-costs.csv").param("start", "2026-09-30").param("end", "2026-09-01")
                        .with(httpBasic("test-admin", "test-password")))
                .andExpect(status().isBadRequest());
    }
}
