package com.reconflow.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Turns messages that exhausted their SQS redrive policy into durable operational
 * exceptions instead of discarding them. Recording is committed before the DLQ
 * listener deletes the message, so a crash can at worst cause a duplicate (deduped
 * by file + exception type), never a lost failure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DlqFailureService {

    public static final String EXCEPTION_TYPE = "DLQ_JOB_FAILED";

    /**
     * Sentinel run id for failures that are not tied to a reconciliation attempt
     * (for example a message that could not be parsed at all). There is no FK on
     * reconciliation_run_id, so this cannot create a dangling constraint.
     */
    private static final UUID SYSTEM_RUN_ID = new UUID(0L, 0L);
    private static final int MAX_NOTES_LENGTH = 4000;

    private final UploadedFileRepository uploadedFileRepository;
    private final ExceptionQueueRepository exceptionQueueRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    /**
     * Durably records a terminal DLQ failure for the file referenced by the message.
     *
     * @return {@code true} once the failure is committed (or was already recorded), so
     *         the caller may safely delete the SQS message.
     */
    @Transactional
    public boolean recordFailure(String messageBody) {
        UUID fileId = extractFileId(messageBody);
        UploadedFile file = uploadedFileRepository.findById(fileId).orElse(null);

        if (file != null) {
            UploadedFileStatus status = file.getStatus();
            if (status == UploadedFileStatus.QUEUED
                    || status == UploadedFileStatus.PROCESSING
                    || status == UploadedFileStatus.PENDING_DEPENDENCY) {
                file.transitionTo(UploadedFileStatus.FAILED);
                uploadedFileRepository.save(file);
            }
            // Only a real file failure may fail the day's run. An unparseable or
            // orphaned DLQ message must not poison an otherwise healthy run.
            markTodaysRunFailed();
        }

        if (!exceptionQueueRepository.existsByMatchIdAndExceptionType(fileId, EXCEPTION_TYPE)) {
            ExceptionQueue exception = ExceptionQueue.builder()
                    .reconciliationRunId(SYSTEM_RUN_ID)
                    .matchId(fileId)
                    .matchType("SYSTEM")
                    .exceptionType(EXCEPTION_TYPE)
                    .currency("N/A")
                    .status(ExceptionStatus.OPEN)
                    .gateway(file != null ? file.getGateway() : null)
                    .resolutionNotes(truncate(messageBody))
                    .build();
            exceptionQueueRepository.save(exception);
            log.error("Recorded DLQ failure for file {} as exception {}", fileId, exception.getId());
        }

        return true;
    }

    /**
     * A run left RUNNING after all retries were exhausted must not stay RUNNING forever.
     * This runs in its own transaction so an unexpected data issue here can never roll
     * back the exception record above.
     */
    private void markTodaysRunFailed() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            template.executeWithoutResult(status ->
                    reconciliationRunRepository.findFirstByRunDateOrderByStartedAtAsc(LocalDate.now()).ifPresent(run -> {
                        if (run.getStatus() == ReconciliationRunStatus.RUNNING) {
                            run.transitionTo(ReconciliationRunStatus.FAILED);
                            reconciliationRunRepository.save(run);
                            log.warn("Marked reconciliation run {} FAILED after DLQ exhaustion", run.getId());
                        }
                    })
            );
        } catch (Exception e) {
            log.warn("Could not mark today's reconciliation run FAILED after DLQ exhaustion", e);
        }
    }

    private UUID extractFileId(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            if (json.hasNonNull("fileId")) {
                return UUID.fromString(json.get("fileId").asText());
            }
        } catch (Exception ignored) {
            // Fall through to the raw-UUID and deterministic-hash shapes.
        }
        try {
            return UUID.fromString(body.trim());
        } catch (Exception ignored) {
            // Deterministic id keeps retries of malformed messages idempotent.
            return UUID.nameUUIDFromBytes(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_NOTES_LENGTH ? value : value.substring(0, MAX_NOTES_LENGTH);
    }
}
