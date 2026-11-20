package com.reconflow.entity.status;

import java.util.Set;

public enum UploadedFileStatus {
    QUEUED, PROCESSING, COMPLETED, FAILED, PENDING_DEPENDENCY;

    public boolean canTransitionTo(UploadedFileStatus next) {
        return switch (this) {
            case QUEUED -> Set.of(PROCESSING, PENDING_DEPENDENCY, FAILED).contains(next);
            // PROCESSING -> QUEUED is the compensation path: a failed/abandoned attempt
            // is returned to the queue for SQS redelivery instead of being lost.
            case PROCESSING -> Set.of(COMPLETED, FAILED, QUEUED).contains(next);
            case PENDING_DEPENDENCY -> Set.of(QUEUED, FAILED).contains(next);
            case COMPLETED, FAILED -> false;
        };
    }
}
