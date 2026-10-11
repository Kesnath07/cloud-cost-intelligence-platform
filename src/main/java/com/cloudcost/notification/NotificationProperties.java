package com.cloudcost.notification;

import java.net.URI;
import java.time.Duration;

import com.cloudcost.common.Severity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Optional webhook notifications for new cost anomalies. Disabled unless {@code webhookUrl} is
 * set. The URL usually embeds a secret token (Slack, Microsoft Teams, Google Chat, Mattermost),
 * so it must come from the environment and is never logged.
 *
 * @param webhookUrl   HTTPS endpoint that accepts a JSON body {@code {"text": "..."}}
 * @param minSeverity  lowest severity that is announced
 * @param lookbackDays only anomalies dated within this many days before today are announced, so
 *                     importing historical data does not flood the channel with old findings
 * @param timeout      connect and read timeout for the webhook request
 */
@Validated
@ConfigurationProperties("costplatform.notification")
public record NotificationProperties(
        URI webhookUrl,
        @DefaultValue("MEDIUM") @NotNull Severity minSeverity,
        @DefaultValue("7") @Min(1) @Max(90) int lookbackDays,
        @DefaultValue("5s") @NotNull Duration timeout) {

    public NotificationProperties {
        if (webhookUrl != null && webhookUrl.toString().isBlank()) {
            webhookUrl = null;
        }
        if (webhookUrl != null && (!"https".equalsIgnoreCase(webhookUrl.getScheme()) || webhookUrl.getHost() == null)) {
            throw new IllegalArgumentException("costplatform.notification.webhook-url must be an absolute https URL");
        }
    }

    public boolean enabled() {
        return webhookUrl != null;
    }
}
