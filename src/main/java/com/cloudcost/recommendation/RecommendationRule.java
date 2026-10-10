package com.cloudcost.recommendation;

import java.util.List;

/** One heuristic that turns stored data into recommendations. Rules never change AWS resources. */
public interface RecommendationRule {

    String id();

    List<RecommendationCandidate> evaluate(RecommendationContext context);
}
