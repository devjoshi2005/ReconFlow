package com.reconflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class AwsConfig {

    @Value("${AWS_REGION:ap-south-1}")
    private String region;

    @Value("${AWS_ACCESS_KEY_ID}")
    private String accessKeyId;

    @Value("${AWS_SECRET_ACCESS_KEY}")
    private String secretAccessKey;

    /**
     * Optional endpoint override. When set (e.g. to a LocalStack edge URL), the SDK
     * talks to that endpoint instead of real AWS. Empty in production.
     */
    @Value("${aws.endpoint:}")
    private String endpoint;

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }

    private boolean hasEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials());
        if (hasEndpoint()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    public SqsClient sqsClient() {
        var builder = SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials());
        if (hasEndpoint()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    public CloudWatchClient cloudWatchClient() {
        var builder = CloudWatchClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials());
        if (hasEndpoint()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
