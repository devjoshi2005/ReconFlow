package com.reconflow.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

import java.net.URI;
import java.util.Map;

/**
 * JVM-wide singleton test infrastructure (Postgres, Redis, LocalStack S3+SQS).
 *
 * <p>Started lazily once per test JVM and torn down by a shutdown hook. Used instead of
 * {@code @Testcontainers} / {@code @Container} on a base class so that the containers are
 * shared across every Spring test context without being stopped between test classes.</p>
 */
public final class TestInfra {

    public static final String BUCKET = "reconflow-test-bucket";
    public static final String QUEUE_NAME = "reconflow-test-queue";
    public static final String DLQ_NAME = "reconflow-test-dlq";

    public static final PostgreSQLContainer<?> POSTGRES;
    public static final GenericContainer<?> REDIS;
    public static final LocalStackContainer LOCALSTACK;

    public static final String QUEUE_URL;
    public static final String DLQ_URL;

    private static final S3Client S3;
    private static final SqsClient SQS;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("reconflow_test")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();

        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());
        REDIS.start();

        LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.5"))
                .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.SQS);
        LOCALSTACK.start();

        URI endpoint = LOCALSTACK.getEndpoint();

        S3 = S3Client.builder()
                .endpointOverride(endpoint)
                .forcePathStyle(true)
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();
        S3.createBucket(b -> b.bucket(BUCKET));

        SQS = SqsClient.builder()
                .endpointOverride(endpoint)
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();

        DLQ_URL = SQS.createQueue(CreateQueueRequest.builder().queueName(DLQ_NAME).build()).queueUrl();
        QUEUE_URL = SQS.createQueue(CreateQueueRequest.builder()
                .queueName(QUEUE_NAME)
                .attributes(Map.of(
                        software.amazon.awssdk.services.sqs.model.QueueAttributeName.REDRIVE_POLICY,
                        "{\"deadLetterTargetArn\":\"" + queueArn(DLQ_URL) + "\",\"maxReceiveCount\":\"3\"}"))
                .build()).queueUrl();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { S3.close(); } catch (Exception ignored) {}
            try { SQS.close(); } catch (Exception ignored) {}
            try { LOCALSTACK.stop(); } catch (Exception ignored) {}
            try { REDIS.stop(); } catch (Exception ignored) {}
            try { POSTGRES.stop(); } catch (Exception ignored) {}
        }));
    }

    private TestInfra() {
    }

    public static void ensureStarted() {
        // No-op: triggering class initialization starts the containers.
    }

    private static String queueArn(String url) {
        return "arn:aws:sqs:" + LOCALSTACK.getRegion() + ":000000000000:"
                + url.substring(url.lastIndexOf('/') + 1);
    }

    public static String redisUrl() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
    }
}
