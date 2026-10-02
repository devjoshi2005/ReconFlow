package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

@Component
public class PhonepeAdapter implements GatewayFileAdapter {

    @Override
    public String getGatewayName() {
        return "PHONEPE";
    }

    @Override
    public GatewaySettlementRecord parse(Map<String, Integer> headerMap, String[] row, CsvHeaderMapper mapper) {
        GatewaySettlementRecord record = new GatewaySettlementRecord();
        record.setGateway(getGatewayName());

        String entityId = mapper.get(headerMap, row, "phonepereferenceid");
        record.setGatewayTransactionId(entityId);

        String orderId = mapper.get(headerMap, row, "merchantreferenceid");
        record.setOrderId(orderId);

        String settlementId = mapper.get(headerMap, row, "bankreferenceno");
        record.setSettlementId(settlementId);

        String amountStr = mapper.get(headerMap, row, "amount");
        if (amountStr != null && !amountStr.isEmpty()) {
            record.setAmount(new BigDecimal(amountStr));
        } else {
            record.setAmount(BigDecimal.ZERO);
        }

        String feeStr = mapper.get(headerMap, row, "fee");
        if (feeStr != null && !feeStr.isEmpty()) {
            record.setFee(new BigDecimal(feeStr).abs());
        }

        String igstStr = mapper.get(headerMap, row, "igst");
        String cgstStr = mapper.get(headerMap, row, "cgst");
        String sgstStr = mapper.get(headerMap, row, "sgst");

        BigDecimal igst = (igstStr != null && !igstStr.isEmpty()) ? new BigDecimal(igstStr).abs() : BigDecimal.ZERO;
        BigDecimal cgst = (cgstStr != null && !cgstStr.isEmpty()) ? new BigDecimal(cgstStr).abs() : BigDecimal.ZERO;
        BigDecimal sgst = (sgstStr != null && !sgstStr.isEmpty()) ? new BigDecimal(sgstStr).abs() : BigDecimal.ZERO;

        record.setTax(igst.add(cgst).add(sgst));

        record.setTransactionDate(Instant.now().atZone(ZoneId.systemDefault())); // Assuming PhonePe CSV doesn't have standard timestamp in our mock

        return record;
    }
}
