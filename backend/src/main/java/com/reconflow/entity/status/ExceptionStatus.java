package com.reconflow.entity.status;

import java.util.Set;

public enum ExceptionStatus {
    OPEN, INVESTIGATING, RESOLVED, REJECTED;

    public boolean canTransitionTo(ExceptionStatus next) {
        return switch (this) {
            case OPEN -> Set.of(INVESTIGATING, RESOLVED, REJECTED).contains(next);
            case INVESTIGATING -> Set.of(RESOLVED, REJECTED).contains(next);
            case RESOLVED, REJECTED -> false;
        };
    }
}
