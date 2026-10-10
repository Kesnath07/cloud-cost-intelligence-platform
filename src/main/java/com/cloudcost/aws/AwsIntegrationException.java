package com.cloudcost.aws;

import com.cloudcost.common.ServiceUnavailableException;

/** Cost Explorer is disabled, unreachable, or rejected a request. Messages are user-facing. */
public class AwsIntegrationException extends ServiceUnavailableException {

    public AwsIntegrationException(String message) {
        super(message);
    }

    public AwsIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
