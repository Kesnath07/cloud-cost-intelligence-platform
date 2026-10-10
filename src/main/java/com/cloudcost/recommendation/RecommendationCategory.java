package com.cloudcost.recommendation;

public enum RecommendationCategory {
    COST_INCREASE("Cost increase"),
    SCHEDULING("Scheduling"),
    TAGGING("Tagging"),
    BUDGET("Budget"),
    RIGHTSIZING("Rightsizing review"),
    IDLE_RESOURCE("Idle resource review");

    private final String label;

    RecommendationCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
