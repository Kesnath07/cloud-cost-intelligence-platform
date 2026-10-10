package com.cloudcost.ingestion;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps the many spellings of AWS service names (Cost and Usage Report product codes, short
 * names, older console names) to the names AWS Cost Explorer returns for the SERVICE
 * dimension, so that data from CSV files and from Cost Explorer aggregates consistently.
 * Unknown names are kept as supplied, with surrounding and repeated whitespace removed.
 */
public final class ServiceNameNormalizer {

    public static final int MAX_LENGTH = 128;

    private static final Map<String, String> CANONICAL = new HashMap<>();

    static {
        register("Amazon Elastic Compute Cloud - Compute",
                "AmazonEC2", "EC2", "Amazon EC2", "Amazon Elastic Compute Cloud", "EC2 - Compute");
        register("EC2 - Other", "EC2-Other");
        register("Amazon Relational Database Service", "AmazonRDS", "RDS", "Amazon RDS");
        register("Amazon Simple Storage Service", "AmazonS3", "S3", "Amazon S3");
        register("AWS Lambda", "AWSLambda", "Lambda");
        register("AmazonCloudWatch", "CloudWatch", "Amazon CloudWatch");
        register("Amazon Virtual Private Cloud", "AmazonVPC", "VPC", "Amazon VPC");
        register("Amazon DynamoDB", "AmazonDynamoDB", "DynamoDB");
        register("Amazon Elastic Load Balancing", "AWSELB", "ELB", "Elastic Load Balancing");
        register("Amazon Elastic Container Service", "AmazonECS", "ECS", "Amazon ECS",
                "Amazon EC2 Container Service");
        register("Amazon EC2 Container Registry (ECR)", "AmazonECR", "ECR", "Amazon ECR",
                "Amazon Elastic Container Registry");
        register("Amazon Elastic Kubernetes Service", "AmazonEKS", "EKS", "Amazon EKS");
        register("AWS Key Management Service", "awskms", "KMS", "AWS KMS");
        register("Amazon CloudFront", "AmazonCloudFront", "CloudFront");
        register("AWS Secrets Manager", "AWSSecretsManager", "Secrets Manager");
        register("Amazon Route 53", "AmazonRoute53", "Route 53", "Route53");
        register("Amazon ElastiCache", "AmazonElastiCache", "ElastiCache");
        register("Amazon Simple Queue Service", "AWSQueueService", "SQS", "Amazon SQS");
        register("Amazon Simple Notification Service", "AmazonSNS", "SNS", "Amazon SNS");
        register("AWS Cost Explorer", "AWSCostExplorer", "Cost Explorer");
        register("Tax");
    }

    private ServiceNameNormalizer() {
    }

    private static void register(String canonical, String... aliases) {
        CANONICAL.put(key(canonical), canonical);
        for (String alias : aliases) {
            CANONICAL.put(key(alias), canonical);
        }
    }

    private static String key(String value) {
        return collapseWhitespace(value).toLowerCase(Locale.ROOT);
    }

    private static String collapseWhitespace(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    /** Returns the canonical service name, or an empty string for blank input. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String cleaned = collapseWhitespace(raw);
        return CANONICAL.getOrDefault(cleaned.toLowerCase(Locale.ROOT), cleaned);
    }
}
