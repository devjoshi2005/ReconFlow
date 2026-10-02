package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;

import java.util.Map;

public interface GatewayFileAdapter {
    String getGatewayName();

    /**
     * Parse the raw row string array into a standard GatewaySettlementRecord
     */
    GatewaySettlementRecord parse(Map<String, Integer> headerMap, String[] row, CsvHeaderMapper mapper);
}
