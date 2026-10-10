package com.cloudcost.aws;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;

/**
 * Creates the Cost Explorer client only when the integration is enabled. Credentials are
 * resolved lazily by the default provider chain (environment variables, shared config,
 * web identity, ECS task role, EC2 instance profile), so the application starts even when
 * none are present; the first request then fails with an actionable message.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "costplatform.aws", name = "enabled", havingValue = "true")
class AwsClientConfig {

    @Bean(destroyMethod = "close")
    CostExplorerClient costExplorerClient(AwsProperties properties) {
        return CostExplorerClient.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofSeconds(20))
                        .apiCallTimeout(Duration.ofSeconds(90))
                        .build())
                .build();
    }
}
