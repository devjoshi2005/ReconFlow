package com.reconflow.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for every Spring integration test. Provides the shared {@link TestInfra} containers
 * and wires them into the Spring Environment. Subclasses keep their own {@code @SpringBootTest}
 * / {@code @AutoConfigureMockMvc} annotations.
 */
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    static {
        TestInfra.ensureStarted();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // Datasource (both canonical keys and the ${DB_*} placeholders in application.yml)
        registry.add("spring.datasource.url", TestInfra.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", TestInfra.POSTGRES::getUsername);
        registry.add("spring.datasource.password", TestInfra.POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("DB_URL", TestInfra.POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", TestInfra.POSTGRES::getUsername);
        registry.add("DB_PASSWORD", TestInfra.POSTGRES::getPassword);

        // Redis (canonical keys + ${REDIS_URL} placeholder)
        registry.add("spring.data.redis.host", TestInfra.REDIS::getHost);
        registry.add("spring.data.redis.port", () -> TestInfra.REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.url", TestInfra::redisUrl);
        registry.add("REDIS_URL", TestInfra::redisUrl);

        // AWS / LocalStack
        registry.add("AWS_REGION", TestInfra.LOCALSTACK::getRegion);
        registry.add("AWS_ACCESS_KEY_ID", TestInfra.LOCALSTACK::getAccessKey);
        registry.add("AWS_SECRET_ACCESS_KEY", TestInfra.LOCALSTACK::getSecretKey);
        registry.add("aws.endpoint", () -> TestInfra.LOCALSTACK.getEndpoint().toString());
        registry.add("aws.region", TestInfra.LOCALSTACK::getRegion);

        registry.add("aws.s3.bucket-name", () -> TestInfra.BUCKET);
        registry.add("AWS_S3_BUCKET_NAME", () -> TestInfra.BUCKET);

        registry.add("aws.sqs.queue-url", () -> TestInfra.QUEUE_URL);
        registry.add("aws.sqs.dlq-url", () -> TestInfra.DLQ_URL);
        registry.add("aws.sqs.queue-name", () -> TestInfra.QUEUE_NAME);
        registry.add("aws.sqs.dlq-name", () -> TestInfra.DLQ_NAME);
        registry.add("AWS_SQS_QUEUE_URL", () -> TestInfra.QUEUE_URL);
        registry.add("AWS_SQS_DLQ_URL", () -> TestInfra.DLQ_URL);
        registry.add("AWS_SQS_QUEUE_NAME", () -> TestInfra.QUEUE_NAME);
        registry.add("AWS_SQS_DLQ_NAME", () -> TestInfra.DLQ_NAME);

        // Deterministic scheduling for tests
        registry.add("scheduling.enabled", () -> "false");
        registry.add("reconflow.sweeper.delay", () -> "86400000");
        registry.add("reconflow.stale-ledger-sweep.fixed-delay", () -> "86400000");
        registry.add("reconflow.queue-recovery.fixed-delay", () -> "86400000");
    }
}
