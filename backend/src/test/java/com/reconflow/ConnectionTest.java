package com.reconflow;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ConnectionTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private SqsClient sqsClient;

    @Test
    void testSupabaseConnection() {
        // Simple DB query to check connection
        Number count = (Number) entityManager.createNativeQuery("SELECT count(*) FROM users").getSingleResult();
        assertThat(count).isNotNull();
        System.out.println("✅ Supabase DB connection successful! User count: " + count);
    }

    @Test
    void testRedisConnection() {
        // Simple CRUD in Redis
        redisTemplate.opsForValue().set("test_key", "test_value");
        String val = redisTemplate.opsForValue().get("test_key");
        
        assertThat(val).isEqualTo("test_value");
        System.out.println("✅ Upstash Redis connection and CRUD successful!");
        
        // Clean up
        redisTemplate.delete("test_key");
    }

    @Test
    void testS3Connection() {
        // Simple S3 API call
        ListBucketsResponse response = s3Client.listBuckets();
        assertThat(response.buckets()).isNotNull();
        System.out.println("✅ AWS S3 connection successful! Visible buckets: " + response.buckets().size());
    }

    @Test
    void testSqsConnection() {
        // Simple SQS API call
        ListQueuesResponse response = sqsClient.listQueues();
        assertThat(response.queueUrls()).isNotNull();
        System.out.println("✅ AWS SQS connection successful! Visible queues: " + response.queueUrls().size());
    }
}
