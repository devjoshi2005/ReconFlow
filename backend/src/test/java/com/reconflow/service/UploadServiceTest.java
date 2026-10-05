package com.reconflow.service;

import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.UploadedFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private SqsClient sqsClient;

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(uploadService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(uploadService, "queueName", "test-queue");
        ReflectionTestUtils.setField(uploadService, "queueUrl", "http://sqs-url");

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void testProcessUpload_Success() throws Exception {
        // Arrange
        String validCsv = "payment_id,order_id,amount,settlement_utr\npay_1,order_1,100,utr_1";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", validCsv.getBytes());
        
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString())).thenReturn(Collections.singletonList(1L));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(uploadedFileRepository.existsByFileChecksum(anyString())).thenReturn(false);
        
        UploadedFile savedFile = UploadedFile.builder()
                .id(UUID.randomUUID())
                .status("QUEUED")
                .build();
        when(uploadedFileRepository.save(any(UploadedFile.class))).thenReturn(savedFile);
        
        // Act
        UploadedFile result = uploadService.processUpload(file, "GATEWAY", "RAZORPAY", null, java.time.LocalDate.now(), "0.01", "10000000");

        // Run afterCommit hooks manually for testing
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

        // Assert
        assertNotNull(result);
        assertEquals("QUEUED", result.getStatus());
        
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testProcessUpload_DuplicateChecksum() throws Exception {
        // Arrange
        String validCsv = "payment_id,order_id,amount,settlement_utr\npay_1,order_1,100,utr_1";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", validCsv.getBytes());
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString())).thenReturn(Collections.singletonList(1L));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(uploadedFileRepository.existsByFileChecksum(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            uploadService.processUpload(file, "GATEWAY", "RAZORPAY", null, java.time.LocalDate.now(), "0.01", "10000000");
        });

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }
}
