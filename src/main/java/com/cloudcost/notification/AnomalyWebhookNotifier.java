package com.cloudcost.notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.cloudcost.analysis.CostFilter;
import com.cloudcost.anomaly.AnomalyFinding;
import com.cloudcost.anomaly.NewAnomaliesEvent;
import com.cloudcost.config.PlatformProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Posts one message per detection run that found new anomalies at or above the configured
 * severity. Delivery is best effort: a failing webhook is logged and never affects detection,
 * imports or synchronization. The message is sent after the anomalies are committed.
 */
@Component
public class AnomalyWebhookNotifier {

    static final int MAX_LISTED = 10;
    private static final Logger log = LoggerFactory.getLogger(AnomalyWebhookNotifier.class);

    private final NotificationProperties properties;
    private final String currency;
    private final RestClient restClient;
    private final Clock clock;

    @Autowired
    public AnomalyWebhookNotifier(NotificationProperties properties, PlatformProperties platformProperties,
            RestClient.Builder restClientBuilder, Clock clock) {
        this(properties, platformProperties, restClientBuilder.requestFactory(requestFactory(properties)).build(),
                clock);
    }

    AnomalyWebhookNotifier(NotificationProperties properties, PlatformProperties platformProperties,
            RestClient restClient, Clock clock) {
        this.properties = properties;
        this.currency = platformProperties.reportingCurrency();
        this.restClient = restClient;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNewAnomalies(NewAnomaliesEvent event) {
        if (!properties.enabled()) {
            return;
        }
        List<AnomalyFinding> announced = selectAnnounced(event.findings());
        if (announced.isEmpty()) {
            return;
        }
        try {
            restClient.post()
                    .uri(properties.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", message(announced)))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Sent anomaly notification for {} finding(s)", announced.size());
        } catch (RestClientResponseException ex) {
            log.warn("Anomaly notification rejected by the webhook with HTTP {}", ex.getStatusCode().value());
        } catch (RestClientException ex) {
            // The exception message can contain the webhook URL, which embeds a secret: log the type only.
            log.warn("Anomaly notification could not be delivered ({})", ex.getClass().getSimpleName());
        }
    }

    /** Findings at or above the minimum severity within the lookback window, most severe and largest first. */
    List<AnomalyFinding> selectAnnounced(List<AnomalyFinding> findings) {
        LocalDate oldest = LocalDate.now(clock).minusDays(properties.lookbackDays());
        return findings.stream()
                .filter(finding -> finding.severity().compareTo(properties.minSeverity()) >= 0)
                .filter(finding -> !finding.date().isBefore(oldest))
                .sorted(Comparator.comparing(AnomalyFinding::severity).reversed()
                        .thenComparing(AnomalyFinding::increase, Comparator.reverseOrder()))
                .toList();
    }

    String message(List<AnomalyFinding> findings) {
        List<String> lines = new ArrayList<>();
        lines.add("Cost anomalies detected: " + findings.size() + " new finding(s) at " + properties.minSeverity()
                + " severity or above.");
        findings.stream().limit(MAX_LISTED).forEach(finding -> lines.add("- " + describe(finding)));
        if (findings.size() > MAX_LISTED) {
            lines.add("- ... and " + (findings.size() - MAX_LISTED) + " more");
        }
        lines.add("Review and acknowledge them on the Anomalies page.");
        return String.join("\n", lines);
    }

    private String describe(AnomalyFinding finding) {
        String value = finding.dimensionValue().isEmpty() ? CostFilter.UNTAGGED_LABEL : finding.dimensionValue();
        String percent = finding.increasePercent() == null ? ""
                : " (+" + finding.increasePercent().setScale(1, RoundingMode.HALF_UP).toPlainString() + "%)";
        return finding.severity() + " " + finding.dimension().label() + " " + value + " on " + finding.date()
                + ": " + money(finding.observed()) + " vs baseline " + money(finding.baseline()) + percent;
    }

    private String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency;
    }

    private static SimpleClientHttpRequestFactory requestFactory(NotificationProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.timeout());
        factory.setReadTimeout(properties.timeout());
        return factory;
    }
}
