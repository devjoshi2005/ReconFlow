package com.reconflow.entity;

import com.reconflow.entity.status.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityTransitionTest {

    @Test
    void uploadedFile_validTransition() {
        UploadedFile file = UploadedFile.builder()
                .status(UploadedFileStatus.QUEUED)
                .build();

        file.transitionTo(UploadedFileStatus.PROCESSING);
        assertThat(file.getStatus()).isEqualTo(UploadedFileStatus.PROCESSING);

        file.transitionTo(UploadedFileStatus.COMPLETED);
        assertThat(file.getStatus()).isEqualTo(UploadedFileStatus.COMPLETED);
    }

    @Test
    void uploadedFile_invalidTransition_throws() {
        UploadedFile file = UploadedFile.builder()
                .status(UploadedFileStatus.QUEUED)
                .build();

        assertThatThrownBy(() -> file.transitionTo(UploadedFileStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid state transition");
    }

    @Test
    void uploadedFile_processingCanBeCompensatedBackToQueued() {
        UploadedFile file = UploadedFile.builder()
                .status(UploadedFileStatus.PROCESSING)
                .build();

        // Compensation path used when a batch job fails and the SQS message is left
        // for redelivery.
        file.transitionTo(UploadedFileStatus.QUEUED);

        assertThat(file.getStatus()).isEqualTo(UploadedFileStatus.QUEUED);
    }

    @Test
    void uploadedFile_terminalState_cannotTransition() {
        UploadedFile file = UploadedFile.builder()
                .status(UploadedFileStatus.COMPLETED)
                .build();

        assertThatThrownBy(() -> file.transitionTo(UploadedFileStatus.QUEUED))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> file.transitionTo(UploadedFileStatus.PROCESSING))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reconciliationRun_setsCompletedAtOnTerminalState() {
        ReconciliationRun run = ReconciliationRun.builder()
                .status(ReconciliationRunStatus.RUNNING)
                .build();

        assertThat(run.getCompletedAt()).isNull();

        run.transitionTo(ReconciliationRunStatus.COMPLETED);

        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.COMPLETED);
        assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void reconciliationRun_failedSetsCompletedAt() {
        ReconciliationRun run = ReconciliationRun.builder()
                .status(ReconciliationRunStatus.RUNNING)
                .build();

        run.transitionTo(ReconciliationRunStatus.FAILED);

        assertThat(run.getStatus()).isEqualTo(ReconciliationRunStatus.FAILED);
        assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void ledgerTransaction_validTransition() {
        LedgerTransaction tx = LedgerTransaction.builder()
                .status(LedgerTransactionStatus.PENDING)
                .build();

        tx.transitionTo(LedgerTransactionStatus.RECONCILED);
        assertThat(tx.getStatus()).isEqualTo(LedgerTransactionStatus.RECONCILED);
    }

    @Test
    void ledgerTransaction_invalidTransition_throws() {
        LedgerTransaction tx = LedgerTransaction.builder()
                .status(LedgerTransactionStatus.RECONCILED)
                .build();

        assertThatThrownBy(() -> tx.transitionTo(LedgerTransactionStatus.PENDING))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ledgerTransaction_previouslyUnreconciledCanBecomeReconciled() {
        LedgerTransaction tx = LedgerTransaction.builder()
                .status(LedgerTransactionStatus.UNRECONCILED)
                .build();

        // The item reader deliberately re-reads UNRECONCILED rows on later runs.
        tx.transitionTo(LedgerTransactionStatus.RECONCILED);

        assertThat(tx.getStatus()).isEqualTo(LedgerTransactionStatus.RECONCILED);
    }

    @Test
    void exceptionQueue_validTransition() {
        ExceptionQueue ex = ExceptionQueue.builder()
                .status(ExceptionStatus.OPEN)
                .build();

        ex.transitionTo(ExceptionStatus.INVESTIGATING);
        assertThat(ex.getStatus()).isEqualTo(ExceptionStatus.INVESTIGATING);

        ex.transitionTo(ExceptionStatus.RESOLVED);
        assertThat(ex.getStatus()).isEqualTo(ExceptionStatus.RESOLVED);
        assertThat(ex.getResolvedAt()).isNotNull();
    }

    @Test
    void exceptionQueue_rejectedSetsResolvedAt() {
        ExceptionQueue ex = ExceptionQueue.builder()
                .status(ExceptionStatus.OPEN)
                .build();

        ex.transitionTo(ExceptionStatus.REJECTED);
        assertThat(ex.getStatus()).isEqualTo(ExceptionStatus.REJECTED);
        assertThat(ex.getResolvedAt()).isNotNull();
    }

    @Test
    void exceptionQueue_invalidTransition_throws() {
        ExceptionQueue ex = ExceptionQueue.builder()
                .status(ExceptionStatus.RESOLVED)
                .build();

        assertThatThrownBy(() -> ex.transitionTo(ExceptionStatus.OPEN))
                .isInstanceOf(IllegalStateException.class);
    }
}
