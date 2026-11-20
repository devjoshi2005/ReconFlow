package com.reconflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.ReconciliationRun;
import com.reconflow.entity.UploadedFile;
import com.reconflow.entity.status.ExceptionStatus;
import com.reconflow.entity.status.ReconciliationRunStatus;
import com.reconflow.entity.status.UploadedFileStatus;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.ReconciliationRunRepository;
import com.reconflow.repository.UploadedFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlqFailureServiceTest {

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Mock
    private ExceptionQueueRepository exceptionQueueRepository;

    @Mock
    private ReconciliationRunRepository reconciliationRunRepository;

    private DlqFailureService service;

    @BeforeEach
    void setUp() {
        service = new DlqFailureService(
                uploadedFileRepository,
                exceptionQueueRepository,
                reconciliationRunRepository,
                new ObjectMapper(),
                new ResourcelessTransactionManager());
    }

    @Test
    void recordsFailureMarksFileFailedAndFailsTodaysRun() {
        UUID fileId = UUID.randomUUID();
        UploadedFile file = UploadedFile.builder()
                .id(fileId)
                .status(UploadedFileStatus.PROCESSING)
                .gateway("RAZORPAY")
                .build();
        ReconciliationRun run = ReconciliationRun.builder()
                .runDate(LocalDate.now())
                .status(ReconciliationRunStatus.RUNNING)
                .build();
        when(uploadedFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(exceptionQueueRepository.existsByMatchIdAndExceptionType(
                fileId, DlqFailureService.EXCEPTION_TYPE)).thenReturn(false);
        when(reconciliationRunRepository.findFirstByRunDateOrderByStartedAtAsc(LocalDate.now()))
                .thenReturn(Optional.of(run));

        assertThat(service.recordFailure("{\"fileId\":\"" + fileId + "\"}")).isTrue();

        assertThat(file.getStatus()).isEqualTo(UploadedFileStatus.FAILED);
        verify(uploadedFileRepository).save(file);

        ArgumentCaptor<ExceptionQueue> captor = ArgumentCaptor.forClass(ExceptionQueue.class);
        verify(exceptionQueueRepository).save(captor.capture());
        ExceptionQueue recorded = captor.getValue();
        assertThat(recorded.getMatchId()).isEqualTo(fileId);
        assertThat(recorded.getMatchType()).isEqualTo("SYSTEM");
        assertThat(recorded.getExceptionType()).isEqualTo(DlqFailureService.EXCEPTION_TYPE);
        assertThat(recorded.getStatus()).isEqualTo(ExceptionStatus.OPEN);
        assertThat(recorded.getGateway()).isEqualTo("RAZORPAY");
        assertThat(recorded.getResolutionNotes()).contains(fileId.toString());

        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.FAILED);
        verify(reconciliationRunRepository).save(run);
    }

    @Test
    void doesNotRecordTheSameDlqFailureTwice() {
        UUID fileId = UUID.randomUUID();
        UploadedFile file = UploadedFile.builder()
                .id(fileId)
                .status(UploadedFileStatus.QUEUED)
                .build();
        when(uploadedFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(exceptionQueueRepository.existsByMatchIdAndExceptionType(
                fileId, DlqFailureService.EXCEPTION_TYPE)).thenReturn(true);

        service.recordFailure("{\"fileId\":\"" + fileId + "\"}");

        verify(exceptionQueueRepository, never()).save(any(ExceptionQueue.class));
    }

    @Test
    void terminalFileIsNotRewritten() {
        UUID fileId = UUID.randomUUID();
        UploadedFile file = UploadedFile.builder()
                .id(fileId)
                .status(UploadedFileStatus.COMPLETED)
                .build();
        when(uploadedFileRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(exceptionQueueRepository.existsByMatchIdAndExceptionType(
                fileId, DlqFailureService.EXCEPTION_TYPE)).thenReturn(false);

        service.recordFailure("{\"fileId\":\"" + fileId + "\"}");

        verify(uploadedFileRepository, never()).save(any(UploadedFile.class));
        verify(exceptionQueueRepository).save(any(ExceptionQueue.class));
    }

    @Test
    void unparseableMessageUsesDeterministicIdAndDoesNotFailTheRun() {
        String body = "not-json";
        UUID deterministicId = UUID.nameUUIDFromBytes(body.getBytes(StandardCharsets.UTF_8));
        when(uploadedFileRepository.findById(deterministicId)).thenReturn(Optional.empty());
        when(exceptionQueueRepository.existsByMatchIdAndExceptionType(
                deterministicId, DlqFailureService.EXCEPTION_TYPE)).thenReturn(false);

        service.recordFailure(body);

        ArgumentCaptor<ExceptionQueue> captor = ArgumentCaptor.forClass(ExceptionQueue.class);
        verify(exceptionQueueRepository).save(captor.capture());
        assertThat(captor.getValue().getMatchId()).isEqualTo(deterministicId);
        assertThat(captor.getValue().getGateway()).isNull();
        verify(reconciliationRunRepository, never())
                .findFirstByRunDateOrderByStartedAtAsc(any(LocalDate.class));
    }

    @Test
    void missingFileDoesNotFailTheRun() {
        UUID fileId = UUID.randomUUID();
        when(uploadedFileRepository.findById(fileId)).thenReturn(Optional.empty());
        when(exceptionQueueRepository.existsByMatchIdAndExceptionType(
                fileId, DlqFailureService.EXCEPTION_TYPE)).thenReturn(false);

        service.recordFailure("{\"fileId\":\"" + fileId + "\"}");

        verify(exceptionQueueRepository).save(any(ExceptionQueue.class));
        verify(reconciliationRunRepository, never())
                .findFirstByRunDateOrderByStartedAtAsc(any(LocalDate.class));
    }
}
