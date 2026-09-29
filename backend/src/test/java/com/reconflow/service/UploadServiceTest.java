package com.reconflow.service;

import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

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

    @InjectMocks
    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(uploadService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(uploadService, "queueName", "test-queue");
    }

    @Test
    void testProcessUpload_Success() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "test data".getBytes());
        
        when(uploadedFileRepository.existsByFileChecksum(anyString())).thenReturn(false);
        
        UploadedFile savedFile = UploadedFile.builder()
                .id(UUID.randomUUID())
                .status("QUEUED")
                .build();
        when(uploadedFileRepository.save(any(UploadedFile.class))).thenReturn(savedFile);
        
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(GetQueueUrlResponse.builder().queueUrl("http://sqs-url").build());

        // Act
        UploadedFile result = uploadService.processUpload(file, "GATEWAY", "RAZORPAY", null);

        // Assert
        assertNotNull(result);
        assertEquals("QUEUED", result.getStatus());
        
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testProcessUpload_DuplicateChecksum() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "test data".getBytes());
        when(uploadedFileRepository.existsByFileChecksum(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            uploadService.processUpload(file, "GATEWAY", "RAZORPAY", null);
        });

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }
}
