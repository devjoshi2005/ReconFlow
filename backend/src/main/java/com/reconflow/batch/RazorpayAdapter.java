package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

@Component
public class RazorpayAdapter implements GatewayFileAdapter {

    @Override
    public String getGatewayName() {
        return "RAZORPAY";
    }

    @Override
    public GatewaySettlementRecord parse(Map<String, Integer> headerMap, String[] row, CsvHeaderMapper mapper) {
        GatewaySettlementRecord record = new GatewaySettlementRecord();
        record.setGateway(getGatewayName());

        String entityId = mapper.get(headerMap, row, "entity_id");
        record.setGatewayTransactionId(entityId);

        String orderId = mapper.get(headerMap, row, "order_id");
        record.setOrderId(orderId);

        String settlementId = mapper.get(headerMap, row, "settlement_id");
        record.setSettlementId(settlementId);

        String utr = mapper.get(headerMap, row, "settlement_utr");
        record.setBankTransferRef(utr);

        String amountStr = mapper.get(headerMap, row, "amount");
        if (amountStr != null && !amountStr.isEmpty()) {
            record.setAmount(new BigDecimal(amountStr).divide(new BigDecimal("100")));
        } else {
            record.setAmount(BigDecimal.ZERO);
        }

        String feeStr = mapper.get(headerMap, row, "fee");
        if (feeStr != null && !feeStr.isEmpty()) {
            record.setFee(new BigDecimal(feeStr).divide(new BigDecimal("100")));
        }

        String taxStr = mapper.get(headerMap, row, "tax");
        if (taxStr != null && !taxStr.isEmpty()) {
            record.setTax(new BigDecimal(taxStr).divide(new BigDecimal("100")));
        }

        String createdAtStr = mapper.get(headerMap, row, "created_at");
        if (createdAtStr != null && !createdAtStr.isEmpty()) {
            long epochSeconds = Long.parseLong(createdAtStr);
            record.setTransactionDate(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()));
        } else {
            record.setTransactionDate(Instant.now().atZone(ZoneId.systemDefault()));
        }

        return record;
    }
}
