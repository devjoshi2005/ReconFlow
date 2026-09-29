package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayReaderTest {

    private final GatewayReaderConfig config = new GatewayReaderConfig();

    @Test
    void testRazorpayReader() throws Exception {
        FlatFileItemReader<GatewaySettlementRecord> reader = config.razorpayCsvReader(null);
        reader.setResource(new ClassPathResource("razorpay_mock.csv"));
        reader.open(new ExecutionContext());

        GatewaySettlementRecord record1 = reader.read();
        assertThat(record1).isNotNull();
        assertThat(record1.getGatewayTransactionId()).isEqualTo("pay_XYZ123");
        assertThat(record1.getSettlementId()).isEqualTo("set_XYZ123");
        assertThat(record1.getType()).isEqualTo("payment");
        assertThat(record1.getAmount()).isEqualByComparingTo(new BigDecimal("1050.00"));
        assertThat(record1.getFee()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(record1.getTax()).isEqualByComparingTo(new BigDecimal("3.60"));
        assertThat(record1.getGateway()).isEqualTo("RAZORPAY");

        GatewaySettlementRecord record2 = reader.read();
        assertThat(record2).isNotNull();
        assertThat(record2.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));

        GatewaySettlementRecord record3 = reader.read();
        assertThat(record3).isNotNull();
        assertThat(record3.getAmount()).isEqualByComparingTo(new BigDecimal("-200.00"));

        assertThat(reader.read()).isNull(); // End of file

        reader.close();
    }

    @Test
    void testPhonePeReader() throws Exception {
        FlatFileItemReader<GatewaySettlementRecord> reader = config.phonePeCsvReader(null);
        reader.setResource(new ClassPathResource("phonepe_mock.csv"));
        reader.open(new ExecutionContext());

        GatewaySettlementRecord record1 = reader.read();
        assertThat(record1).isNotNull();
        assertThat(record1.getGatewayTransactionId()).isEqualTo("T2310011000000000001");
        assertThat(record1.getAmount()).isEqualByComparingTo(new BigDecimal("1050.00"));
        assertThat(record1.getStatus()).isEqualTo("SUCCESS");
        assertThat(record1.getSettlementId()).isEqualTo("UTR001"); // UTR mapped to settlementId
        assertThat(record1.getGateway()).isEqualTo("PHONEPE");

        GatewaySettlementRecord record2 = reader.read();
        assertThat(record2).isNotNull();

        GatewaySettlementRecord record3 = reader.read();
        assertThat(record3).isNotNull();
        assertThat(record3.getStatus()).isEqualTo("FAILED");

        assertThat(reader.read()).isNull(); // End of file

        reader.close();
    }
}
