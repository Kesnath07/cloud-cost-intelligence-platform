package com.cloudcost.aws;

import static com.cloudcost.aws.CostExplorerResponses.day;
import static com.cloudcost.aws.CostExplorerResponses.group;
import static com.cloudcost.aws.CostExplorerResponses.page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.cloudcost.common.ConflictException;
import com.cloudcost.common.DateRange;
import com.cloudcost.common.InvalidRequestException;
import com.cloudcost.costdata.CostImportJob;
import com.cloudcost.costdata.CostImportJobRepository;
import com.cloudcost.costdata.CostSource;
import com.cloudcost.costdata.ImportStatus;
import com.cloudcost.support.DatabaseCleaner;
import com.cloudcost.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.CostExplorerException;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;

@PostgresIntegrationTest
@TestPropertySource(properties = "costplatform.aws.enabled=true")
class CostExplorerSyncServiceIntegrationTest {

    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEP_2 = LocalDate.of(2026, 9, 2);

    @MockitoBean
    private CostExplorerClient client;

    @Autowired
    private CostExplorerSyncService syncService;

    @Autowired
    private CostImportJobRepository jobs;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
    }

    private BigDecimal storedTotal() {
        return jdbc.queryForObject("select coalesce(sum(amount), 0) from cost_record where source = ?",
                BigDecimal.class, CostSource.AWS_COST_EXPLORER.name());
    }

    @Test
    void resynchronizingReplacesRevisedFiguresInsteadOfDuplicatingThem() {
        when(client.getCostAndUsage(any(GetCostAndUsageRequest.class)))
                .thenReturn(page(null, day(SEP_1, true, group("AWS Lambda", "Environment$prod", "5.00")),
                        day(SEP_2, true, group("AWS Lambda", "Environment$prod", "6.00"))))
                .thenReturn(page(null, day(SEP_1, false, group("AWS Lambda", "Environment$prod", "5.50")),
                        day(SEP_2, false, group("AWS Lambda", "Environment$prod", "6.25"))));

        CostImportJob first = syncService.sync(SEP_1, SEP_2);
        CostImportJob second = syncService.sync(SEP_1, SEP_2);

        assertThat(first.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(first.getRecordsInserted()).isEqualTo(2);
        assertThat(second.getRecordsReplaced()).isEqualTo(2);
        assertThat(storedTotal()).isEqualByComparingTo("11.75");
        assertThat(jdbc.queryForObject("select count(*) from cost_record where estimated", Integer.class)).isZero();
    }

    @Test
    void failedRequestsAreRecordedWithAnActionableMessage() {
        when(client.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenThrow(CostExplorerException.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
                .message("denied")
                .build());

        assertThatThrownBy(() -> syncService.sync(SEP_1, SEP_2))
                .isInstanceOf(AwsIntegrationException.class)
                .hasMessageContaining("ce:GetCostAndUsage");
        assertThat(jobs.findAll()).singleElement().satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(ImportStatus.FAILED);
            assertThat(job.getErrorMessage()).contains("ce:GetCostAndUsage");
        });
    }

    @Test
    void invalidRangesAreRejectedBeforeCallingAws() {
        assertThatThrownBy(() -> syncService.sync(SEP_2, SEP_1)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> syncService.sync(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("at most 90 days");
        assertThatThrownBy(() -> syncService.sync(LocalDate.now(), LocalDate.now().plusDays(1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("future");
        verifyNoInteractions(client);
    }

    @Test
    void onlyOneSynchronizationCanRunAtATime() {
        jobs.saveAndFlush(CostImportJob.startSync(DateRange.of(SEP_1, SEP_2), Instant.now()));

        assertThatThrownBy(() -> syncService.sync(SEP_1, SEP_2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already running");
        verifyNoInteractions(client);
    }
}
