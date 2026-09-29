package com.reconflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewaySettlementRecord {
    private String gatewayTransactionId;
    private String orderId; // Usually the internal order ID that the gateway sends back
    private String settlementId;
    private String type; // payment, refund
    private BigDecimal amount;
    private BigDecimal fee;
    private BigDecimal tax;
    private String status;
    private ZonedDateTime transactionDate;
    private String gateway; // RAZORPAY, PHONEPE
}
