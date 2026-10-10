package com.cloudcost.anomaly;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AnomalyReviewRequest(
        @NotNull(message = "Choose a status") AnomalyStatus status,
        @Size(max = 500, message = "Use at most 500 characters") String note) {
}
