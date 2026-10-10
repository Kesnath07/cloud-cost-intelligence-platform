package com.cloudcost.ingestion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Limits for file imports. Upload size itself is limited by
 * {@code spring.servlet.multipart.max-file-size}.
 *
 * @param maxRows           maximum data rows accepted in a single file
 * @param maxReportedErrors validation errors listed per failed import before truncating
 */
@Validated
@ConfigurationProperties("costplatform.import")
public record ImportProperties(
        @DefaultValue("500000") @Min(1) int maxRows,
        @DefaultValue("100") @Min(1) @Max(1000) int maxReportedErrors) {
}
