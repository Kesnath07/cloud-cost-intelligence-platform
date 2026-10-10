package com.cloudcost.aws;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.Group;
import software.amazon.awssdk.services.costexplorer.model.MetricValue;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;

/** Builders for realistic GetCostAndUsage responses used by tests. */
final class CostExplorerResponses {

    private CostExplorerResponses() {
    }

    static Group group(String service, String tagGroupKey, String amount) {
        return group(service, tagGroupKey, amount, "USD");
    }

    static Group group(String service, String tagGroupKey, String amount, String unit) {
        return Group.builder()
                .keys(service, tagGroupKey)
                .metrics(Map.of("UnblendedCost", MetricValue.builder().amount(amount).unit(unit).build()))
                .build();
    }

    static ResultByTime day(LocalDate date, boolean estimated, Group... groups) {
        return ResultByTime.builder()
                .timePeriod(DateInterval.builder().start(date.toString()).end(date.plusDays(1).toString()).build())
                .estimated(estimated)
                .groups(List.of(groups))
                .build();
    }

    static GetCostAndUsageResponse page(String nextPageToken, ResultByTime... days) {
        return GetCostAndUsageResponse.builder().resultsByTime(days).nextPageToken(nextPageToken).build();
    }
}
