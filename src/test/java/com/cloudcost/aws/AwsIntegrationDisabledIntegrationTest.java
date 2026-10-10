package com.cloudcost.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.cloudcost.support.PostgresIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import software.amazon.awssdk.services.costexplorer.CostExplorerClient;

/** The default configuration must start and work without any AWS credentials or client. */
@PostgresIntegrationTest
class AwsIntegrationDisabledIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CostExplorerSyncService syncService;

    @Test
    void noCostExplorerClientIsCreatedAndSyncExplainsHowToEnableIt() {
        assertThat(context.getBeanNamesForType(CostExplorerClient.class)).isEmpty();
        assertThat(syncService.isEnabled()).isFalse();
        assertThatThrownBy(() -> syncService.sync(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)))
                .isInstanceOf(AwsIntegrationException.class)
                .hasMessageContaining("COSTPLATFORM_AWS_ENABLED=true");
    }
}
