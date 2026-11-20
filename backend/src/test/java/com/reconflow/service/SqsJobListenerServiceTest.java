package com.reconflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconflow.entity.UploadedFile;
import com.reconflow.entity.status.UploadedFileStatus;
import com.reconflow.repository.ReconciliationRunRepository;
import com.reconflow.repository.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsJobListenerServiceTest {

    @Mock
    private SqsClient sqsClient;
    @Mock
    private UploadedFileRepository uploadedFileRepository;
    @Mock
    private ReconciliationRunRepository reconciliationRunRepository;
    @Mock
    private JobLauncher jobLauncher;
    @Mock
    private Job gatewayReconciliationJob;
    @Mock
    private Job bankReconciliationJob;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ObjectMapper objectMapper;

    private SqsJobListenerService service;

    @BeforeEach
    void setUp() {
        service = new SqsJobListenerService(
                sqsClient,
                uploadedFileRepository,
                reconciliationRunRepository,
                jobLauncher,
                gatewayReconciliationJob,
                bankReconciliationJob,
                redisTemplate,
                objectMapper);
    }

    @Test
    void compensateToQueued_requeuesProcessingFile() {
        UUID fileId = UUID.randomUUID();
        UploadedFile file = UploadedFile.builder()
                .id(fileId)
                .status(UploadedFileStatus.PROCESSING)
                .build();
        when(uploadedFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        ReflectionTestUtils.invokeMethod(service, "compensateToQueued", fileId);

        assertThat(file.getStatus()).isEqualTo(UploadedFileStatus.QUEUED);
        verify(uploadedFileRepository).save(file);
    }

    @Test
    void compensateToQueued_leavesTerminalFileUntouched() {
        UUID fileId = UUID.randomUUID();
        UploadedFile file = UploadedFile.builder()
                .id(fileId)
                .status(UploadedFileStatus.COMPLETED)
                .build();
        when(uploadedFileRepository.findById(fileId)).thenReturn(Optional.of(file));

        ReflectionTestUtils.invokeMethod(service, "compensateToQueued", fileId);

        assertThat(file.getStatus()).isEqualTo(UploadedFileStatus.COMPLETED);
        verify(uploadedFileRepository, never()).save(any(UploadedFile.class));
    }

    @Test
    void compensateToQueued_isNoOpWhenFileIsMissing() {
        UUID fileId = UUID.randomUUID();
        when(uploadedFileRepository.findById(fileId)).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(service, "compensateToQueued", fileId);

        verify(uploadedFileRepository, never()).save(any(UploadedFile.class));
    }
}
