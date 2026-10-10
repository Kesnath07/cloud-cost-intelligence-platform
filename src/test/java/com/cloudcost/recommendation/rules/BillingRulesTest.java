package com.cloudcost.recommendation.rules;

import static com.cloudcost.recommendation.rules.RuleTestSupport.CURRENT;
import static com.cloudcost.recommendation.rules.RuleTestSupport.DEFAULTS;
import static com.cloudcost.recommendation.rules.RuleTestSupport.context;
import static com.cloudcost.recommendation.rules.RuleTestSupport.daily;
import static com.cloudcost.recommendation.rules.RuleTestSupport.sum;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import com.cloudcost.common.Severity;
import com.cloudcost.recommendation.EvidenceSource;
import com.cloudcost.recommendation.RecommendationCandidate;
import com.cloudcost.recommendation.RecommendationCategory;
import com.cloudcost.recommendation.RecommendationContext;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BillingRulesTest {

    private static final String RDS = "Amazon Relational Database Service";

    @Nested
    class SustainedCostIncrease {

        private final SustainedCostIncreaseRule rule = new SustainedCostIncreaseRule(DEFAULTS);

        private List<RecommendationCandidate> evaluate(NavigableMap<LocalDate, BigDecimal> current, String previous) {
            RecommendationContext ctx = context(Map.of(RDS, sum(current)), Map.of(RDS, new BigDecimal(previous)),
                    Map.of(RDS, current), Map.of(), Map.of(), List.of(), List.of());
            return rule.evaluate(ctx);
        }

        @Test
        void flagsAnIncreasePresentThroughoutTheWindow() {
            List<RecommendationCandidate> candidates = evaluate(daily(CURRENT, date -> "54.00"), "1140.00");

            assertThat(candidates).singleElement().satisfies(c -> {
                assertThat(c.title()).isEqualTo("Sustained cost increase: " + RDS + " (+42.11%)");
                assertThat(c.category()).isEqualTo(RecommendationCategory.COST_INCREASE);
                assertThat(c.evidenceSource()).isEqualTo(EvidenceSource.BILLING_DATA);
                assertThat(c.severity()).as("+480.00").isEqualTo(Severity.MEDIUM);
                assertThat(c.serviceName()).isEqualTo(RDS);
                assertThat(c.estimatedMonthlySavings()).isNull();
                assertThat(c.evidence()).anySatisfy(line -> assertThat(line).contains("1620.00 USD", "1140.00 USD"));
                assertThat(c.fingerprint()).isEqualTo("SUSTAINED_INCREASE|" + RDS + "|2026-09");
            });
        }

        @Test
        void ignoresAOneOffSpikeEvenIfThePeriodTotalRose() {
            NavigableMap<LocalDate, BigDecimal> spike = daily(CURRENT,
                    date -> date.getDayOfMonth() == 25 ? "800.00" : "38.00");

            assertThat(evaluate(spike, "1140.00")).isEmpty();
        }

        @Test
        void ignoresIncreasesBelowEitherThreshold() {
            assertThat(evaluate(daily(CURRENT, date -> "41.00"), "1140.00")).as("+7.9%").isEmpty();
            assertThat(evaluate(daily(CURRENT, date -> "3.00"), "60.00")).as("+50% but only +30.00").isEmpty();
        }

        @Test
        void ignoresNewServicesWithoutABaseline() {
            assertThat(evaluate(daily(CURRENT, date -> "54.00"), "0")).isEmpty();
        }

        @Test
        void requiresEnoughDaysWithData() {
            RecommendationContext sparse = new RecommendationContext(CURRENT, CURRENT.previousPeriod(), 30, 10,
                    new BigDecimal("1620"), Map.of(RDS, new BigDecimal("1620")), Map.of(RDS, new BigDecimal("380")),
                    Map.of(RDS, daily(CURRENT, date -> "54.00")), Map.of(), Map.of(), List.of(), List.of(), "USD");

            assertThat(rule.evaluate(sparse)).isEmpty();
        }
    }

    @Nested
    class NonProductionSchedule {

        private final NonProductionScheduleRule rule = new NonProductionScheduleRule(DEFAULTS);

        private static boolean weekend(LocalDate date) {
            return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        }

        private List<RecommendationCandidate> evaluate(String environment, NavigableMap<LocalDate, BigDecimal> series) {
            return rule.evaluate(context(Map.of(), Map.of(), Map.of(), Map.of(environment, series), Map.of(),
                    List.of(), List.of()));
        }

        @Test
        void flagsNonProductionEnvironmentsThatCostTheSameOnWeekends() {
            List<RecommendationCandidate> candidates = evaluate("development",
                    daily(CURRENT, date -> weekend(date) ? "29.50" : "30.00"));

            assertThat(candidates).singleElement().satisfies(c -> {
                assertThat(c.category()).isEqualTo(RecommendationCategory.SCHEDULING);
                assertThat(c.environment()).isEqualTo("development");
                assertThat(c.evidence()).anySatisfy(line -> assertThat(line).contains("98.33% of the weekday average"));
                assertThat(c.explanation()).contains("does not prove");
                assertThat(c.estimatedMonthlySavings()).isNull();
                assertThat(c.severity()).as("8 weekend days x 29.50 = 236.00").isEqualTo(Severity.MEDIUM);
            });
        }

        @Test
        void ignoresEnvironmentsThatScaleDownOnWeekends() {
            assertThat(evaluate("development", daily(CURRENT, date -> weekend(date) ? "10.00" : "30.00"))).isEmpty();
        }

        @Test
        void ignoresProductionAndTrivialAmounts() {
            assertThat(evaluate("production", daily(CURRENT, date -> "300.00"))).isEmpty();
            assertThat(evaluate("staging", daily(CURRENT, date -> "2.00"))).isEmpty();
        }

        @Test
        void handlesZeroWeekdaySpendWithoutFailing() {
            assertThat(evaluate("staging", daily(CURRENT, date -> weekend(date) ? "10.00" : "0"))).isEmpty();
        }
    }

    @Nested
    class UntaggedSpend {

        private final UntaggedSpendRule rule = new UntaggedSpendRule(DEFAULTS);

        @Test
        void flagsASignificantUntaggedShareAndListsTheLargestServices() {
            RecommendationContext ctx = context(Map.of("EC2", new BigDecimal("900"), "S3", new BigDecimal("100")),
                    Map.of(), Map.of(), Map.of(),
                    Map.of("EC2", new BigDecimal("150"), "S3", new BigDecimal("40"), "KMS", new BigDecimal("10")),
                    List.of(), List.of());

            assertThat(rule.evaluate(ctx)).singleElement().satisfies(c -> {
                assertThat(c.title()).contains("20.00% of spend");
                assertThat(c.severity()).isEqualTo(Severity.MEDIUM);
                assertThat(c.evidence().get(1)).isEqualTo(
                        "Largest untagged services: EC2 (150.00 USD), S3 (40.00 USD), KMS (10.00 USD).");
            });
        }

        @Test
        void ignoresSmallShares() {
            RecommendationContext ctx = context(Map.of("EC2", new BigDecimal("1000")), Map.of(), Map.of(), Map.of(),
                    Map.of("EC2", new BigDecimal("50")), List.of(), List.of());

            assertThat(rule.evaluate(ctx)).isEmpty();
        }

        @Test
        void doesNotDivideByANonPositiveTotal() {
            RecommendationContext ctx = context(Map.of("Credits", new BigDecimal("-100")), Map.of(), Map.of(),
                    Map.of(), Map.of("Credits", new BigDecimal("60")), List.of(), List.of());

            assertThat(rule.evaluate(ctx)).isEmpty();
        }
    }
}
