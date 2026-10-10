package com.cloudcost.config;

import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Platform-wide settings.
 *
 * @param reportingCurrency ISO 4217 code that all stored cost amounts must use. Currency
 *                          conversion is intentionally not supported; data in any other
 *                          currency is rejected at ingestion time.
 */
@Validated
@ConfigurationProperties("costplatform")
public record PlatformProperties(
        @DefaultValue("USD") @Pattern(regexp = "[A-Z]{3}") String reportingCurrency) {
}
