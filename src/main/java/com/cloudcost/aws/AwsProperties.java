package com.cloudcost.aws;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * AWS Cost Explorer integration settings. The integration is disabled by default so that the
 * application runs locally without AWS credentials. Credentials are never configured here:
 * the AWS SDK default credential provider chain is used.
 *
 * @param enabled                   whether Cost Explorer synchronization is available
 * @param region                    Cost Explorer API endpoint region (the API is served from us-east-1)
 * @param environmentTagKey         cost allocation tag that identifies the environment
 * @param costMetric                Cost Explorer metric to store
 * @param maxSyncDays               largest date range a single synchronization may request
 * @param maxPages                  safety limit on paginated API calls per synchronization
 * @param scheduledSyncEnabled      whether to synchronize automatically
 * @param scheduledSyncCron         cron expression (UTC) for automatic synchronization
 * @param scheduledSyncLookbackDays days (ending yesterday) refreshed by each automatic run
 * @param staleJobTimeout           age after which a RUNNING synchronization is considered interrupted
 */
@Validated
@ConfigurationProperties("costplatform.aws")
public record AwsProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("us-east-1") @NotBlank String region,
        @DefaultValue("Environment") @NotBlank @Pattern(regexp = "[\\p{L}\\p{N} _.:/=+@-]{1,128}") String environmentTagKey,
        @DefaultValue("UnblendedCost")
        @Pattern(regexp = "UnblendedCost|AmortizedCost|NetUnblendedCost|NetAmortizedCost|BlendedCost")
        String costMetric,
        @DefaultValue("90") @Min(1) @Max(366) int maxSyncDays,
        @DefaultValue("100") @Min(1) int maxPages,
        @DefaultValue("false") boolean scheduledSyncEnabled,
        @DefaultValue("0 30 6 * * *") String scheduledSyncCron,
        @DefaultValue("7") @Min(1) @Max(90) int scheduledSyncLookbackDays,
        @DefaultValue("PT1H") Duration staleJobTimeout) {
}
