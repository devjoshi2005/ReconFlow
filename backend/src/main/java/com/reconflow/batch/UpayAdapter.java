package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class UpayAdapter implements GatewayFileAdapter {

    @Override
    public String getGatewayName() {
        return "PAYU"; // The frontend sends PAYU, so we must match it
    }

    @Override
    public GatewaySettlementRecord parse(Map<String, Integer> headerMap, String[] row, CsvHeaderMapper mapper) {
        GatewaySettlementRecord record = new GatewaySettlementRecord();
        record.setGateway(getGatewayName());

        record.setGatewayTransactionId(mapper.get(headerMap, row, "paymentid"));
        record.setOrderId(mapper.get(headerMap, row, "orderid"));
        
        String amountStr = mapper.get(headerMap, row, "grossamount");
        if (amountStr != null && !amountStr.isEmpty()) {
            record.setAmount(new BigDecimal(amountStr));
        } else {
            record.setAmount(BigDecimal.ZERO);
        }

        String feeStr = mapper.get(headerMap, row, "mdr");
        if (feeStr != null && !feeStr.isEmpty()) {
            record.setFee(new BigDecimal(feeStr).abs());
        }

        String taxStr = mapper.get(headerMap, row, "gstonmdr");
        if (taxStr != null && !taxStr.isEmpty()) {
            record.setTax(new BigDecimal(taxStr).abs());
        }

        String settlementId = mapper.get(headerMap, row, "settlementutr");
        record.setSettlementId(settlementId);
        record.setBankTransferRef(settlementId);
        
        String dateStr = mapper.get(headerMap, row, "transactiondate");
        if (dateStr != null && !dateStr.isEmpty()) {
            dateStr = dateStr + " 00:00:00";
            record.setTransactionDate(LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss")).atZone(ZoneId.systemDefault()));
        } else {
            record.setTransactionDate(LocalDateTime.now().atZone(ZoneId.systemDefault()));
        }

        return record;
    }
}
