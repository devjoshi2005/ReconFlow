package com.reconflow.entity.status;

import java.util.Set;

public enum LedgerTransactionStatus {
    PENDING, RECONCILED, UNRECONCILED, STALE;

    public boolean canTransitionTo(LedgerTransactionStatus next) {
        return switch (this) {
            case PENDING -> Set.of(RECONCILED, UNRECONCILED, STALE).contains(next);
            // UNRECONCILED rows are intentionally re-read by the next reconciliation run,
            // so a corrected gateway file may legitimately reconcile them later.
            case UNRECONCILED -> next == RECONCILED;
            case RECONCILED, STALE -> false;
        };
    }
}
