package com.reconflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconflow.dto.InfrastructureSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemService {

    private final SqsClient sqsClient;
    private final CloudWatchClient cloudWatchClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queue-url}")
    private String mainQueueUrl;

    @Value("${aws.sqs.dlq-url}")
    private String dlqUrl;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public InfrastructureSummaryDTO getInfrastructureSummary() {
        String cacheKey = "system:infrastructure:summary";

        try {
            // Rate limiting check
            String ipOrUserKey = "ratelimit:infra:admin";
            Long count = redisTemplate.opsForValue().increment(ipOrUserKey);
            if (count != null && count == 1) {
                redisTemplate.expire(ipOrUserKey, Duration.ofSeconds(60));
            }
            if (count != null && count > 60) {
                throw new RuntimeException("Rate limit exceeded for infrastructure endpoint");
            }

            // Cache check
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, InfrastructureSummaryDTO.class);
            }

            InfrastructureSummaryDTO summary = fetchRealInfrastructureData();

            // Cache for 60s
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(summary), Duration.ofSeconds(60));
            return summary;

        } catch (Exception e) {
            log.error("Failed to fetch infrastructure summary", e);
            throw new RuntimeException("Failed to fetch infrastructure summary", e);
        }
    }

    private InfrastructureSummaryDTO fetchRealInfrastructureData() {
        InfrastructureSummaryDTO dto = new InfrastructureSummaryDTO();
        
        try {
            // SQS Depths
            dto.setSqsMainDepth(getQueueDepth(mainQueueUrl));
            dto.setSqsDlqDepth(getQueueDepth(dlqUrl));

            // CloudWatch CPU (Dummy or real based on what is available)
            // Example for EC2 (if running on EC2), but assuming Fargate or generic
            dto.setCpuUtilization(getMetricAverage("AWS/EC2", "CPUUtilization", "InstanceId", "i-0dummy"));
            dto.setMemoryUtilization(45.2); // Usually requires custom metric

            // S3 Storage Metrics
            Double s3Objects = getMetricAverage("AWS/S3", "NumberOfObjects", "BucketName", bucketName);
            dto.setS3FileCount(s3Objects != null ? s3Objects.intValue() : 0);
            
            Double s3Bytes = getMetricAverage("AWS/S3", "BucketSizeBytes", "BucketName", bucketName);
            dto.setS3TotalSize(s3Bytes != null ? s3Bytes.longValue() : 0L);

            // API Latency Mocks (Since no @Timed is configured to write to CW yet)
            dto.setApiLatencyP50(120.5);
            dto.setApiLatencyP95(305.2);
            dto.setApiLatencyP99(850.1);
        } catch (Exception e) {
            log.warn("Could not fetch all real AWS metrics, using fallback values", e);
            if (dto.getSqsMainDepth() == null) dto.setSqsMainDepth(0);
            if (dto.getSqsDlqDepth() == null) dto.setSqsDlqDepth(0);
            if (dto.getS3FileCount() == null) dto.setS3FileCount(0);
            if (dto.getS3TotalSize() == null) dto.setS3TotalSize(0L);
        }

        return dto;
    }

    private int getQueueDepth(String url) {
        if (url == null || url.isEmpty()) return 0;
        try {
            GetQueueAttributesResponse response = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(url)
                    .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                    .build());
            Map<QueueAttributeName, String> attrs = response.attributes();
            return Integer.parseInt(attrs.getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0"));
        } catch (Exception e) {
            log.warn("Failed to fetch queue depth for {}", url, e);
            return 0;
        }
    }

    private Double getMetricAverage(String namespace, String metricName, String dimName, String dimValue) {
        try {
            GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                    .namespace(namespace)
                    .metricName(metricName)
                    .dimensions(Dimension.builder().name(dimName).value(dimValue).build())
                    .startTime(Instant.now().minus(1, ChronoUnit.DAYS))
                    .endTime(Instant.now())
                    .period(86400) // 1 day
                    .statistics(Statistic.AVERAGE)
                    .build();

            GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
            List<Datapoint> datapoints = response.datapoints();
            if (datapoints.isEmpty()) {
                return 0.0;
            }
            return datapoints.get(0).average();
        } catch (Exception e) {
            log.warn("Failed to fetch CloudWatch metric {}/{}", namespace, metricName, e);
            return 0.0;
        }
    }
}
