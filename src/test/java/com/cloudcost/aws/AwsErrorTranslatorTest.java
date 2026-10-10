package com.cloudcost.aws;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.costexplorer.model.CostExplorerException;

class AwsErrorTranslatorTest {

    private static CostExplorerException serviceError(String code, String rawMessage) {
        return (CostExplorerException) CostExplorerException.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).errorMessage(rawMessage).build())
                .requestId("req-123")
                .message(rawMessage)
                .build();
    }

    @Test
    void accessDeniedExplainsTheRequiredPermissionWithoutLeakingTheRawMessage() {
        AwsIntegrationException translated = AwsErrorTranslator.translate(serviceError("AccessDeniedException",
                "User: arn:aws:sts::111122223333:assumed-role/secret-role is not authorized"), "Environment");

        assertThat(translated.getMessage())
                .contains("ce:GetCostAndUsage")
                .contains("req-123")
                .doesNotContain("arn:aws")
                .doesNotContain("111122223333");
    }

    @Test
    void missingCredentialsPointToTheDefaultProviderChain() {
        AwsIntegrationException translated = AwsErrorTranslator.translate(
                SdkClientException.create("Unable to load credentials from any of the providers in the chain"),
                "Environment");

        assertThat(translated.getMessage()).contains("No AWS credentials were found");
    }

    @Test
    void throttlingAndUnknownErrorsAreActionable() {
        assertThat(AwsErrorTranslator.translate(serviceError("LimitExceededException", "x"), "Environment")
                .getMessage()).contains("rate limit");
        assertThat(AwsErrorTranslator.translate(serviceError("SomethingNew", "x"), "Environment")
                .getMessage()).contains("SomethingNew");
        assertThat(AwsErrorTranslator.translate(SdkClientException.create("connect timed out"), "Environment")
                .getMessage()).contains("Could not reach AWS Cost Explorer");
    }
}
