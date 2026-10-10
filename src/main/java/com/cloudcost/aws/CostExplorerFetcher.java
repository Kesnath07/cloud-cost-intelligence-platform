package com.cloudcost.aws;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cloudcost.common.DateRange;
import com.cloudcost.config.PlatformProperties;
import com.cloudcost.costdata.ChargeType;
import com.cloudcost.ingestion.EnvironmentNormalizer;
import com.cloudcost.ingestion.NormalizedCostRecord;
import com.cloudcost.ingestion.ServiceNameNormalizer;

import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinition;
import software.amazon.awssdk.services.costexplorer.model.GroupDefinitionType;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;

/**
 * Reads daily costs from {@code GetCostAndUsage}, grouped by SERVICE and by the environment
 * cost allocation tag (Cost Explorer allows at most two group-by keys, so account and region
 * are not broken down). Every paginated call is billed by AWS, which is why the number of
 * pages is capped.
 */
class CostExplorerFetcher {

    static final String SERVICE_DIMENSION = "SERVICE";

    private final CostExplorerClient client;
    private final AwsProperties properties;
    private final String reportingCurrency;

    CostExplorerFetcher(CostExplorerClient client, AwsProperties properties, PlatformProperties platform) {
        this.client = client;
        this.properties = properties;
        this.reportingCurrency = platform.reportingCurrency();
    }

    record FetchResult(List<NormalizedCostRecord> records, int pages) {
    }

    FetchResult fetch(DateRange range) {
        GetCostAndUsageRequest.Builder request = GetCostAndUsageRequest.builder()
                // Cost Explorer end dates are exclusive.
                .timePeriod(DateInterval.builder()
                        .start(range.start().toString())
                        .end(range.end().plusDays(1).toString())
                        .build())
                .granularity(Granularity.DAILY)
                .metrics(properties.costMetric())
                .groupBy(GroupDefinition.builder().type(GroupDefinitionType.DIMENSION).key(SERVICE_DIMENSION).build(),
                        GroupDefinition.builder().type(GroupDefinitionType.TAG)
                                .key(properties.environmentTagKey()).build());

        Map<NormalizedCostRecord.NaturalKey, NormalizedCostRecord> records = new LinkedHashMap<>();
        String nextPageToken = null;
        int pages = 0;
        do {
            if (pages >= properties.maxPages()) {
                throw new AwsIntegrationException("Cost Explorer returned more than " + properties.maxPages()
                        + " pages. Synchronize a shorter date range.");
            }
            GetCostAndUsageResponse response = client.getCostAndUsage(request.nextPageToken(nextPageToken).build());
            pages++;
            for (ResultByTime result : response.resultsByTime()) {
                LocalDate day = LocalDate.parse(result.timePeriod().start());
                boolean estimated = Boolean.TRUE.equals(result.estimated());
                for (Group group : result.groups()) {
                    merge(records, toRecord(day, estimated, group));
                }
            }
            nextPageToken = response.nextPageToken();
        } while (nextPageToken != null && !nextPageToken.isEmpty());
        return new FetchResult(new ArrayList<>(records.values()), pages);
    }

    private NormalizedCostRecord toRecord(LocalDate day, boolean estimated, Group group) {
        List<String> keys = group.keys();
        String service = ServiceNameNormalizer.normalize(keys.isEmpty() ? "" : keys.get(0));
        if (service.isEmpty()) {
            service = "Unknown service";
        } else if (service.length() > ServiceNameNormalizer.MAX_LENGTH) {
            service = service.substring(0, ServiceNameNormalizer.MAX_LENGTH);
        }
        String environment = EnvironmentNormalizer.sanitize(tagValue(keys.size() > 1 ? keys.get(1) : ""));
        MetricValue metric = group.metrics().get(properties.costMetric());
        if (metric == null || metric.amount() == null) {
            throw new AwsIntegrationException("Cost Explorer response did not contain the metric "
                    + properties.costMetric() + ".");
        }
        if (!reportingCurrency.equals(metric.unit())) {
            throw new AwsIntegrationException("Cost Explorer returned amounts in " + metric.unit()
                    + " but the reporting currency is " + reportingCurrency + "; currency conversion is not supported.");
        }
        BigDecimal amount = new BigDecimal(metric.amount()).setScale(6, RoundingMode.HALF_UP);
        return new NormalizedCostRecord(day, "", service, "", environment, ChargeType.NET, amount,
                reportingCurrency, estimated);
    }

    /** Tag group keys have the form {@code Key$value}; untagged costs are reported as {@code Key$}. */
    static String tagValue(String groupKey) {
        int separator = groupKey.indexOf('$');
        return separator < 0 ? groupKey : groupKey.substring(separator + 1);
    }

    /** Different raw tag values can normalize to the same environment (Prod, prod); their costs are summed. */
    private static void merge(Map<NormalizedCostRecord.NaturalKey, NormalizedCostRecord> records,
            NormalizedCostRecord record) {
        records.merge(record.naturalKey(), record, (existing, added) -> new NormalizedCostRecord(
                existing.usageDate(), existing.accountId(), existing.serviceName(), existing.region(),
                existing.environment(), existing.chargeType(), existing.amount().add(added.amount()),
                existing.currency(), existing.estimated() || added.estimated()));
    }
}
