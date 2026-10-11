package com.cloudcost.anomaly;

import java.util.List;

/**
 * Published by an anomaly detection run that stored new findings. Refreshed or previously
 * reviewed anomalies are not included, so each finding is announced once.
 */
public record NewAnomaliesEvent(List<AnomalyFinding> findings) {

    public NewAnomaliesEvent {
        findings = List.copyOf(findings);
    }
}
