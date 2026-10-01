package com.reconflow.dto;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
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
    @CsvBindByName(column = "gatewayTransactionId")
    private String gatewayTransactionId;
    
    @CsvBindByName(column = "orderId", required = false)
    private String orderId;
    
    @CsvBindByName(column = "settlementId", required = false)
    private String settlementId;
    
    @CsvBindByName(column = "type", required = false)
    private String type;
    
    @CsvBindByName(column = "amount")
    private BigDecimal amount;
    
    @CsvBindByName(column = "fee", required = false)
    private BigDecimal fee;
    
    @CsvBindByName(column = "tax", required = false)
    private BigDecimal tax;
    
    @CsvBindByName(column = "status", required = false)
    private String status;
    
    @CsvBindByName(column = "transactionDateStr", required = false)
    @CsvDate("yyyy-MM-dd HH:mm:ssX")
    private ZonedDateTime transactionDate;
    
    @CsvBindByName(column = "gateway", required = false)
    private String gateway;
}
