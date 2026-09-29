package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class GatewayReaderConfig {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    @StepScope
    public FlatFileItemReader<GatewaySettlementRecord> razorpayCsvReader(@Value("#{jobParameters['filePath']}") String filePath) {
        return new FlatFileItemReaderBuilder<GatewaySettlementRecord>()
                .name("razorpayCsvReader")
                .resource(new FileSystemResource(filePath != null ? filePath : "dummy.csv"))
                .delimited()
                .names("gatewayTransactionId", "settlementId", "type", "amount", "fee", "tax", "transactionDateStr")
                .linesToSkip(1)
                .fieldSetMapper(fs -> {
                    GatewaySettlementRecord record = new GatewaySettlementRecord();
                    record.setGatewayTransactionId(fs.readString("gatewayTransactionId"));
                    record.setSettlementId(fs.readString("settlementId"));
                    record.setType(fs.readString("type"));
                    record.setAmount(fs.readBigDecimal("amount"));
                    record.setFee(fs.readBigDecimal("fee"));
                    record.setTax(fs.readBigDecimal("tax"));
                    record.setGateway("RAZORPAY");
                    String dateStr = fs.readString("transactionDateStr");
                    record.setTransactionDate(LocalDateTime.parse(dateStr, DATE_FORMAT).atZone(ZoneId.systemDefault()));
                    return record;
                })
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<GatewaySettlementRecord> phonePeCsvReader(@Value("#{jobParameters['filePath']}") String filePath) {
        return new FlatFileItemReaderBuilder<GatewaySettlementRecord>()
                .name("phonePeCsvReader")
                .resource(new FileSystemResource(filePath != null ? filePath : "dummy.csv"))
                .delimited()
                .names("gatewayTransactionId", "amount", "status", "utr", "transactionDateStr")
                .linesToSkip(1)
                .fieldSetMapper(fs -> {
                    GatewaySettlementRecord record = new GatewaySettlementRecord();
                    record.setGatewayTransactionId(fs.readString("gatewayTransactionId"));
                    record.setAmount(fs.readBigDecimal("amount"));
                    record.setStatus(fs.readString("status"));
                    record.setSettlementId(fs.readString("utr"));
                    record.setGateway("PHONEPE");
                    String dateStr = fs.readString("transactionDateStr");
                    record.setTransactionDate(LocalDateTime.parse(dateStr, DATE_FORMAT).atZone(ZoneId.systemDefault()));
                    return record;
                })
                .build();
    }
}
