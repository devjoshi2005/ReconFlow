package com.reconflow.dto;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatementRecord {
    @CsvBindByName(column = "bankName", required = false)
    private String bankName;
    
    @CsvBindByName(column = "utr")
    private String utr;
    
    @CsvBindByName(column = "accountNo", required = false)
    private String accountNo;
    
    @CsvBindByName(column = "creditAmount")
    private BigDecimal creditAmount;
    
    @CsvBindByName(column = "transactionDateStr", required = false)
    @CsvDate("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime transactionDate;
    
    @CsvBindByName(column = "narration", required = false)
    private String narration;
}
