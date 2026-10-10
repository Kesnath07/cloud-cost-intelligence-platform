package com.cloudcost.recommendation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecommendationStatusRequest(
        @NotNull(message = "Choose a status") RecommendationStatus status,
        @Size(max = 500, message = "Use at most 500 characters") String note) {
}
