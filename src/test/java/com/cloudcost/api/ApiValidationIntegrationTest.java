package com.cloudcost.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudcost.support.DatabaseCleaner;
import com.cloudcost.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** API authentication, validation and error responses (RFC 9457 problem details). */
@PostgresIntegrationTest
@AutoConfigureMockMvc
class ApiValidationIntegrationTest {

    private static final RequestPostProcessor ADMIN = httpBasic("test-admin", "test-password");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/costs/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/costs/summary").with(httpBasic("test-admin", "wrong"))).andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidDateRanges() throws Exception {
        mvc.perform(get("/api/costs/summary").param("start", "2026-09-30").param("end", "2026-09-01").with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("must be on or before")));
        mvc.perform(get("/api/costs/summary").param("start", "2026-09-01").with(ADMIN))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/costs/summary").param("start", "30/09/2026").param("end", "2026-10-01").with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("YYYY-MM-DD")));
        mvc.perform(get("/api/costs/summary").param("start", "2020-01-01").param("end", "2026-01-01").with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("limited to")));
    }

    @Test
    void returnsAnEmptySummaryWhenNoDataIsStored() throws Exception {
        mvc.perform(get("/api/costs/summary").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.daysWithData").value(0))
                .andExpect(jsonPath("$.changePercent").doesNotExist());
    }

    @Test
    void reportsBudgetFieldErrors() throws Exception {
        mvc.perform(post("/api/budgets").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "", "limitAmount": 0, "period": "MONTHLY", "warningThresholdPercent": 150}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.limitAmount").value("The limit must be greater than zero"))
                .andExpect(jsonPath("$.fieldErrors.warningThresholdPercent").exists());
    }

    @Test
    void createsBudgetsAndRejectsDuplicateNames() throws Exception {
        String body = """
                {"name": "Monthly total", "limitAmount": 500, "period": "MONTHLY", "warningThresholdPercent": 80}
                """;
        mvc.perform(post("/api/budgets").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evaluation.status").value("NORMAL"));
        mvc.perform(post("/api/budgets").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundForUnknownResources() throws Exception {
        mvc.perform(get("/api/budgets/999").with(ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Budget 999 was not found"));
        mvc.perform(delete("/api/imports/999").with(ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsMalformedJsonWithoutLeakingParserDetails() throws Exception {
        mvc.perform(post("/api/budgets").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$", not(hasKey("trace"))));
    }

    @Test
    void reportsCsvValidationErrorsWithRowNumbers() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "costs.csv", "text/csv",
                "usage_date,service,amount,currency\n2026-09-01,EC2,abc,USD\n".getBytes());

        mvc.perform(multipart("/api/imports/costs").file(file).with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("Row 2, column 'amount'")));
    }

    @Test
    void rejectsDuplicateFileUploadsWithConflict() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "costs.csv", "text/csv",
                "usage_date,service,amount,currency\n2026-09-01,EC2,1.00,USD\n".getBytes());

        mvc.perform(multipart("/api/imports/costs").file(file).with(ADMIN)).andExpect(status().isCreated());
        mvc.perform(multipart("/api/imports/costs").file(file).with(ADMIN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already imported")));
    }

    @Test
    void awsSyncReportsThatTheIntegrationIsDisabled() throws Exception {
        mvc.perform(post("/api/aws/sync").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start\": \"2026-09-01\", \"end\": \"2026-09-02\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail", containsString("COSTPLATFORM_AWS_ENABLED")));
    }
}
