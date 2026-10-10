package com.cloudcost.aws;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * Converts AWS SDK exceptions into actionable, user-facing messages. Raw SDK messages are
 * not passed through because they can contain profile names, ARNs or other account details;
 * the AWS request ID is included so that failures can be correlated with CloudTrail.
 */
final class AwsErrorTranslator {

    private AwsErrorTranslator() {
    }

    static AwsIntegrationException translate(SdkException ex, String tagKey) {
        if (ex instanceof AwsServiceException service) {
            String code = service.awsErrorDetails() == null ? null : service.awsErrorDetails().errorCode();
            String message = serviceMessage(code, tagKey);
            if (service.requestId() != null) {
                message += " (AWS request ID " + service.requestId() + ")";
            }
            return new AwsIntegrationException(message, ex);
        }
        if (ex instanceof SdkClientException && ex.getMessage() != null
                && ex.getMessage().contains("Unable to load credentials")) {
            return new AwsIntegrationException("No AWS credentials were found. Provide them through the default "
                    + "credential provider chain (environment variables, a shared AWS profile, or an IAM role for "
                    + "the ECS task) or disable the Cost Explorer integration.", ex);
        }
        return new AwsIntegrationException("Could not reach AWS Cost Explorer. Check network access to the "
                + "Cost Explorer endpoint (ce.us-east-1.amazonaws.com) and try again.", ex);
    }

    private static String serviceMessage(String code, String tagKey) {
        if (code == null) {
            return "Cost Explorer request failed.";
        }
        return switch (code) {
            case "AccessDeniedException", "AccessDenied", "UnauthorizedOperation" ->
                    "AWS denied the Cost Explorer request. Grant ce:GetCostAndUsage to the application's IAM "
                            + "identity and make sure Cost Explorer is enabled for the account "
                            + "(see docs/aws-integration.md).";
            case "ExpiredTokenException", "InvalidClientTokenId", "UnrecognizedClientException",
                    "SignatureDoesNotMatch" ->
                    "The AWS credentials in use are invalid or expired. Refresh them and try again.";
            case "DataUnavailableException" ->
                    "Cost Explorer has no data for the requested period. Data can take up to 24 hours to "
                            + "appear after Cost Explorer is enabled.";
            case "BillExpirationException" ->
                    "The requested period is older than the cost history Cost Explorer provides for this account.";
            case "LimitExceededException", "ThrottlingException", "TooManyRequestsException" ->
                    "The Cost Explorer request rate limit was reached. Wait a few minutes and try again.";
            case "RequestChangedException", "InvalidNextTokenException" ->
                    "Cost Explorer data changed while it was being read. Run the synchronization again.";
            case "ValidationException" ->
                    "Cost Explorer rejected the request parameters. Check the date range and the configured "
                            + "environment tag key '" + tagKey + "'.";
            default -> "Cost Explorer request failed with error code " + code + ".";
        };
    }
}
