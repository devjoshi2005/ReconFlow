package com.reconflow.entity.status;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateMachineTest {

    @Test
    void uploadedFileStatus_validTransitions() {
        assertThat(UploadedFileStatus.QUEUED.canTransitionTo(UploadedFileStatus.PROCESSING)).isTrue();
        assertThat(UploadedFileStatus.QUEUED.canTransitionTo(UploadedFileStatus.PENDING_DEPENDENCY)).isTrue();
        assertThat(UploadedFileStatus.QUEUED.canTransitionTo(UploadedFileStatus.FAILED)).isTrue();

        assertThat(UploadedFileStatus.PROCESSING.canTransitionTo(UploadedFileStatus.COMPLETED)).isTrue();
        assertThat(UploadedFileStatus.PROCESSING.canTransitionTo(UploadedFileStatus.FAILED)).isTrue();
        // Compensation path for a failed/abandoned attempt awaiting SQS redelivery.
        assertThat(UploadedFileStatus.PROCESSING.canTransitionTo(UploadedFileStatus.QUEUED)).isTrue();

        assertThat(UploadedFileStatus.PENDING_DEPENDENCY.canTransitionTo(UploadedFileStatus.QUEUED)).isTrue();
        assertThat(UploadedFileStatus.PENDING_DEPENDENCY.canTransitionTo(UploadedFileStatus.FAILED)).isTrue();
    }

    @Test
    void uploadedFileStatus_invalidTransitions() {
        assertThat(UploadedFileStatus.COMPLETED.canTransitionTo(UploadedFileStatus.QUEUED)).isFalse();
        assertThat(UploadedFileStatus.COMPLETED.canTransitionTo(UploadedFileStatus.PROCESSING)).isFalse();
        assertThat(UploadedFileStatus.FAILED.canTransitionTo(UploadedFileStatus.QUEUED)).isFalse();
        assertThat(UploadedFileStatus.QUEUED.canTransitionTo(UploadedFileStatus.COMPLETED)).isFalse();
        assertThat(UploadedFileStatus.QUEUED.canTransitionTo(UploadedFileStatus.QUEUED)).isFalse();
    }

    @Test
    void reconciliationRunStatus_validTransitions() {
        assertThat(ReconciliationRunStatus.RUNNING.canTransitionTo(ReconciliationRunStatus.COMPLETED)).isTrue();
        assertThat(ReconciliationRunStatus.RUNNING.canTransitionTo(ReconciliationRunStatus.FAILED)).isTrue();
    }

    @Test
    void reconciliationRunStatus_invalidTransitions() {
        assertThat(ReconciliationRunStatus.COMPLETED.canTransitionTo(ReconciliationRunStatus.RUNNING)).isFalse();
        assertThat(ReconciliationRunStatus.FAILED.canTransitionTo(ReconciliationRunStatus.RUNNING)).isFalse();
        assertThat(ReconciliationRunStatus.RUNNING.canTransitionTo(ReconciliationRunStatus.RUNNING)).isFalse();
    }

    @Test
    void ledgerTransactionStatus_validTransitions() {
        assertThat(LedgerTransactionStatus.PENDING.canTransitionTo(LedgerTransactionStatus.RECONCILED)).isTrue();
        assertThat(LedgerTransactionStatus.PENDING.canTransitionTo(LedgerTransactionStatus.UNRECONCILED)).isTrue();
        assertThat(LedgerTransactionStatus.PENDING.canTransitionTo(LedgerTransactionStatus.STALE)).isTrue();
        // The item reader re-reads UNRECONCILED rows, so a later corrected gateway
        // file may legitimately reconcile them.
        assertThat(LedgerTransactionStatus.UNRECONCILED.canTransitionTo(LedgerTransactionStatus.RECONCILED)).isTrue();
    }

    @Test
    void ledgerTransactionStatus_invalidTransitions() {
        assertThat(LedgerTransactionStatus.RECONCILED.canTransitionTo(LedgerTransactionStatus.PENDING)).isFalse();
        assertThat(LedgerTransactionStatus.UNRECONCILED.canTransitionTo(LedgerTransactionStatus.PENDING)).isFalse();
        assertThat(LedgerTransactionStatus.UNRECONCILED.canTransitionTo(LedgerTransactionStatus.STALE)).isFalse();
        assertThat(LedgerTransactionStatus.STALE.canTransitionTo(LedgerTransactionStatus.PENDING)).isFalse();
        assertThat(LedgerTransactionStatus.PENDING.canTransitionTo(LedgerTransactionStatus.PENDING)).isFalse();
    }

    @Test
    void exceptionStatus_validTransitions() {
        assertThat(ExceptionStatus.OPEN.canTransitionTo(ExceptionStatus.INVESTIGATING)).isTrue();
        assertThat(ExceptionStatus.OPEN.canTransitionTo(ExceptionStatus.RESOLVED)).isTrue();
        assertThat(ExceptionStatus.OPEN.canTransitionTo(ExceptionStatus.REJECTED)).isTrue();

        assertThat(ExceptionStatus.INVESTIGATING.canTransitionTo(ExceptionStatus.RESOLVED)).isTrue();
        assertThat(ExceptionStatus.INVESTIGATING.canTransitionTo(ExceptionStatus.REJECTED)).isTrue();
    }

    @Test
    void exceptionStatus_invalidTransitions() {
        assertThat(ExceptionStatus.RESOLVED.canTransitionTo(ExceptionStatus.OPEN)).isFalse();
        assertThat(ExceptionStatus.RESOLVED.canTransitionTo(ExceptionStatus.INVESTIGATING)).isFalse();
        assertThat(ExceptionStatus.REJECTED.canTransitionTo(ExceptionStatus.OPEN)).isFalse();
        assertThat(ExceptionStatus.OPEN.canTransitionTo(ExceptionStatus.OPEN)).isFalse();
        assertThat(ExceptionStatus.INVESTIGATING.canTransitionTo(ExceptionStatus.OPEN)).isFalse();
    }
}
