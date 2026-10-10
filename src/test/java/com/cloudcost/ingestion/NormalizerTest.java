package com.cloudcost.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NormalizerTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "AmazonEC2, Amazon Elastic Compute Cloud - Compute",
            "'  amazon   ec2 ', Amazon Elastic Compute Cloud - Compute",
            "AmazonRDS, Amazon Relational Database Service",
            "S3, Amazon Simple Storage Service",
            "awskms, AWS Key Management Service",
            "CloudWatch, AmazonCloudWatch",
            "EC2-Other, EC2 - Other",
            "'Some   New  Service', Some New Service"
    })
    void mapsServiceAliasesToCostExplorerNames(String raw, String expected) {
        assertThat(ServiceNameNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "Prod, production",
            "' PRD ', production",
            "dev, development",
            "stg, staging",
            "QA, qa",
            "'', ''"
    })
    void normalizesEnvironmentTagValues(String raw, String expected) {
        assertThat(EnvironmentNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'Team A / Prod', team-a-prod",
            "'--weird--', weird--",
            "'ok_value.1', ok_value.1"
    })
    void sanitizesUntrustedTagValuesFromCostExplorer(String raw, String expected) {
        String sanitized = EnvironmentNormalizer.sanitize(raw);
        assertThat(sanitized).isEqualTo(expected);
        assertThat(EnvironmentNormalizer.isValid(sanitized)).isTrue();
    }
}
