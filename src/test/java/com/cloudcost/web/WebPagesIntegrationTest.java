package com.cloudcost.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.cloudcost.budget.BudgetForm;
import com.cloudcost.budget.BudgetPeriod;
import com.cloudcost.budget.BudgetService;
import com.cloudcost.ingestion.CostIngestionService;
import com.cloudcost.support.DatabaseCleaner;
import com.cloudcost.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Renders every page with an empty database and with the demonstration dataset loaded. */
@PostgresIntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class WebPagesIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CostIngestionService ingestion;

    @Autowired
    private BudgetService budgets;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/explorer", "/services", "/explorer?service=AWS+Lambda&environment=(untagged)", "/?start=2026-09-30&end=2026-09-01", "/data-sources", "/budgets", "/budgets/new", "/anomalies", "/anomalies?status=ALL", "/recommendations", "/recommendations?status=ALL", "/reports", "/reports?service=AWS+Lambda&environment=(untagged)"})
    void pagesRenderWithoutData(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Demonstration data is loaded"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/explorer", "/services", "/explorer?service=AWS+Lambda&environment=(untagged)", "/?start=2026-09-30&end=2026-09-01", "/data-sources", "/budgets", "/budgets/new", "/anomalies", "/anomalies?status=ALL", "/recommendations", "/recommendations?status=ALL", "/reports", "/reports?service=AWS+Lambda&environment=(untagged)"})
    void pagesRenderWithDemonstrationDataAndLabelIt(String path) throws Exception {
        ingestion.loadDemoDataset();

        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Demonstration data is loaded")));
    }

    @Test
    void budgetBarsRenderOnTheOverviewAndBudgetPages() throws Exception {
        ingestion.loadDemoDataset();
        BudgetForm form = new BudgetForm();
        form.setName("Production monthly");
        form.setLimitAmount(new BigDecimal("5000.00"));
        form.setPeriod(BudgetPeriod.MONTHLY);
        form.setEnvironment("production");
        form.setWarningThresholdPercent(80);
        budgets.create(form);

        for (String path : new String[] {"/", "/budgets"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Production monthly")))
                    .andExpect(content().string(containsString("EXCEEDED")));
        }
    }

    @Test
    void invalidPeriodsFallBackToTheDefaultWithAMessage() throws Exception {
        ingestion.loadDemoDataset();

        mvc.perform(get("/").param("start", "2026-09-30").param("end", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Showing the default period instead")));
    }

    @Test
    void emptyDashboardPointsToDataSources() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No cost data available")));
    }
}
