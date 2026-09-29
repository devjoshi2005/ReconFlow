package com.reconflow.dto;

import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.LedgerTransaction;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReconciliationResult {
    private GatewaySettlement gatewaySettlement;
    private LedgerTransaction matchedLedgerTransaction;
    private ExceptionQueue exception;
    
    public boolean hasException() {
        return exception != null;
    }
}
