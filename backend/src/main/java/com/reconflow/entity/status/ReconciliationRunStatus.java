package com.reconflow.entity.status;

import java.util.Set;

public enum ReconciliationRunStatus {
    RUNNING, COMPLETED, FAILED;

    public boolean canTransitionTo(ReconciliationRunStatus next) {
        return switch (this) {
            case RUNNING -> Set.of(COMPLETED, FAILED).contains(next);
            case COMPLETED, FAILED -> false;
        };
    }
}
