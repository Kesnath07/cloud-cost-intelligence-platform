package com.cloudcost.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.cloudcost.analysis.CostDimension;
import com.cloudcost.anomaly.AnomalyFinding;
import com.cloudcost.anomaly.NewAnomaliesEvent;
import com.cloudcost.common.Severity;
import com.cloudcost.config.PlatformProperties;
import com.cloudcost.support.TestClocks;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AnomalyWebhookNotifierTest {

    private static final URI WEBHOOK = URI.create("https://hooks.example.test/services/T000/B000/secret");
    /** TestClocks.NOW is 2026-10-10, so the default 7-day lookback starts on 2026-10-03. */
    private static final LocalDate RECENT = LocalDate.of(2026, 10, 8);

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private AnomalyWebhookNotifier notifier(URI webhookUrl) {
        NotificationProperties properties = new NotificationProperties(webhookUrl, Severity.MEDIUM, 7,
                Duration.ofSeconds(5));
        return new AnomalyWebhookNotifier(properties, new PlatformProperties("USD"), builder.build(),
                TestClocks.fixed());
    }

    private static AnomalyFinding finding(Severity severity, String value, LocalDate date, String observed,
            String baseline) {
        BigDecimal increase = new BigDecimal(observed).subtract(new BigDecimal(baseline));
        return new AnomalyFinding(CostDimension.SERVICE, value, date, new BigDecimal(observed),
                new BigDecimal(baseline), increase, new BigDecimal("125.004"), 14, severity, "Spike");
    }

    @Test
    void postsOneMessageWithTheAnnouncedFindingsMostSevereFirst() {
        server.expect(requestTo(WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.text", allOf(
                        containsString("2 new finding(s) at MEDIUM severity or above"),
                        containsString("- HIGH Service Amazon EC2 on 2026-10-08: 900.00 USD vs baseline 400.00 USD (+125.0%)"),
                        containsString("- MEDIUM Service AWS Lambda"),
                        not(containsString("Amazon S3")),
                        not(containsString("2026-09-01")))))
                .andRespond(withSuccess());

        notifier(WEBHOOK).onNewAnomalies(new NewAnomaliesEvent(List.of(
                finding(Severity.MEDIUM, "AWS Lambda", RECENT, "200.00", "80.00"),
                finding(Severity.LOW, "Amazon S3", RECENT, "60.00", "30.00"),
                finding(Severity.HIGH, "Amazon EC2", RECENT, "900.00", "400.00"),
                finding(Severity.HIGH, "Amazon RDS", LocalDate.of(2026, 9, 1), "900.00", "400.00"))));

        server.verify();
    }

    @Test
    void sendsNothingWhenDisabledOrWhenNoFindingQualifies() {
        notifier(null).onNewAnomalies(new NewAnomaliesEvent(List.of(
                finding(Severity.HIGH, "Amazon EC2", RECENT, "900.00", "400.00"))));
        notifier(WEBHOOK).onNewAnomalies(new NewAnomaliesEvent(List.of(
                finding(Severity.LOW, "Amazon S3", RECENT, "60.00", "30.00"))));

        server.verify();
    }

    @Test
    void webhookFailuresAreSwallowed() {
        server.expect(requestTo(WEBHOOK)).andRespond(withServerError());

        notifier(WEBHOOK).onNewAnomalies(new NewAnomaliesEvent(List.of(
                finding(Severity.HIGH, "Amazon EC2", RECENT, "900.00", "400.00"))));

        server.verify();
    }

    @Test
    void listsAtMostTenFindingsAndLabelsUntaggedEnvironments() {
        List<AnomalyFinding> findings = new ArrayList<>();
        findings.add(new AnomalyFinding(CostDimension.ENVIRONMENT, "", RECENT, new BigDecimal("500"),
                new BigDecimal("100"), new BigDecimal("400"), null, 14, Severity.HIGH, "Spike"));
        for (int i = 0; i < 11; i++) {
            findings.add(finding(Severity.MEDIUM, "Service " + i, RECENT, "200.00", "80.00"));
        }

        String message = notifier(WEBHOOK).message(findings);

        assertThat(message).contains("- HIGH Environment (untagged) on 2026-10-08: 500.00 USD vs baseline 100.00 USD\n")
                .contains("- ... and 2 more")
                .endsWith("Review and acknowledge them on the Anomalies page.");
        assertThat(message.lines().filter(line -> line.startsWith("- ")).count()).isEqualTo(11);
    }

    @Test
    void requiresAnAbsoluteHttpsWebhookUrlAndTreatsBlankAsDisabled() {
        assertThatThrownBy(() -> new NotificationProperties(URI.create("http://hooks.example.test/x"),
                Severity.MEDIUM, 7, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
        assertThat(new NotificationProperties(URI.create(""), Severity.MEDIUM, 7, Duration.ofSeconds(5)).enabled())
                .isFalse();
    }
}
