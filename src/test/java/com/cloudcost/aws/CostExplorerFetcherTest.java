package com.cloudcost.aws;

import static com.cloudcost.aws.CostExplorerResponses.day;
import static com.cloudcost.aws.CostExplorerResponses.group;
import static com.cloudcost.aws.CostExplorerResponses.page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import com.cloudcost.common.DateRange;
import com.cloudcost.config.PlatformProperties;
import com.cloudcost.costdata.ChargeType;
import com.cloudcost.ingestion.NormalizedCostRecord;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;

class CostExplorerFetcherTest {

    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEP_2 = LocalDate.of(2026, 9, 2);

    private final CostExplorerClient client = mock(CostExplorerClient.class);

    private CostExplorerFetcher fetcher(int maxPages) {
        AwsProperties properties = new AwsProperties(true, "us-east-1", "Environment", "UnblendedCost", 90, maxPages,
                false, "0 30 6 * * *", 7, Duration.ofHours(1));
        return new CostExplorerFetcher(client, properties, new PlatformProperties("USD"));
    }

    @Test
    void requestsDailyCostsGroupedByServiceAndEnvironmentTagWithExclusiveEndDate() {
        when(client.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenReturn(page(null));

        fetcher(10).fetch(DateRange.of(SEP_1, SEP_2));

        ArgumentCaptor<GetCostAndUsageRequest> request = ArgumentCaptor.forClass(GetCostAndUsageRequest.class);
        verify(client).getCostAndUsage(request.capture());
        assertThat(request.getValue().timePeriod().start()).isEqualTo("2026-09-01");
        assertThat(request.getValue().timePeriod().end()).isEqualTo("2026-09-03");
        assertThat(request.getValue().granularityAsString()).isEqualTo("DAILY");
        assertThat(request.getValue().metrics()).containsExactly("UnblendedCost");
        assertThat(request.getValue().groupBy()).satisfiesExactly(
                first -> assertThat(first.key()).isEqualTo("SERVICE"),
                second -> {
                    assertThat(second.type()).isEqualTo(GroupDefinitionType.TAG);
                    assertThat(second.key()).isEqualTo("Environment");
                });
    }

    @Test
    void followsPaginationAndNormalizesGroups() {
        when(client.getCostAndUsage(any(GetCostAndUsageRequest.class)))
                .thenReturn(page("token-2", day(SEP_1, false,
                        group("Amazon Elastic Compute Cloud - Compute", "Environment$Prod", "10.1234567891"),
                        group("Amazon Simple Storage Service", "Environment$", "2.5"))))
                .thenReturn(page(null, day(SEP_2, true,
                        group("AWS Lambda", "Environment$dev", "0.000001"))));

        CostExplorerFetcher.FetchResult result = fetcher(10).fetch(DateRange.of(SEP_1, SEP_2));

        assertThat(result.pages()).isEqualTo(2);
        List<NormalizedCostRecord> records = result.records();
        assertThat(records).hasSize(3);
        assertThat(records.get(0).environment()).isEqualTo("production");
        assertThat(records.get(0).amount()).isEqualByComparingTo("10.123457");
        assertThat(records.get(0).chargeType()).isEqualTo(ChargeType.NET);
        assertThat(records.get(1).environment()).isEmpty();
        assertThat(records.get(2).estimated()).isTrue();
        assertThat(records.get(2).environment()).isEqualTo("development");
        verify(client, times(2)).getCostAndUsage(any(GetCostAndUsageRequest.class));
    }

    @Test
    void sumsTagValuesThatNormalizeToTheSameEnvironment() {
        when(client.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenReturn(page(null, day(SEP_1, false,
                group("AWS Lambda", "Environment$Prod", "1.25"),
                group("AWS Lambda", "Environment$production", "0.75"))));

        List<NormalizedCostRecord> records = fetcher(10).fetch(DateRange.of(SEP_1, SEP_1)).records();

        assertThat(records).singleElement()
                .satisfies(record -> assertThat(record.amount()).isEqualByComparingTo("2.00"));
    }

    @Test
    void rejectsAmountsInAnotherCurrency() {
        when(client.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenReturn(page(null, day(SEP_1, false,
                group("AWS Lambda", "Environment$prod", "1.00", "EUR"))));

        assertThatThrownBy(() -> fetcher(10).fetch(DateRange.of(SEP_1, SEP_1)))
                .isInstanceOf(AwsIntegrationException.class)
                .hasMessageContaining("EUR");
    }

    @Test
    void stopsAfterTheConfiguredNumberOfPages() {
        when(client.getCostAndUsage(any(GetCostAndUsageRequest.class))).thenReturn(page("again"));

        assertThatThrownBy(() -> fetcher(3).fetch(DateRange.of(SEP_1, SEP_1)))
                .isInstanceOf(AwsIntegrationException.class)
                .hasMessageContaining("more than 3 pages");
        verify(client, times(3)).getCostAndUsage(any(GetCostAndUsageRequest.class));
    }

    @Test
    void extractsTagValuesFromGroupKeys() {
        assertThat(CostExplorerFetcher.tagValue("Environment$staging")).isEqualTo("staging");
        assertThat(CostExplorerFetcher.tagValue("Environment$")).isEmpty();
        assertThat(CostExplorerFetcher.tagValue("Environment$a$b")).isEqualTo("a$b");
    }
}
