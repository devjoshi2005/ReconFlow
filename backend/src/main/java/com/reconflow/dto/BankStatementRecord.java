package com.reconflow.dto;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatementRecord {
    @CsvBindByName(column = "bankName", required = false)
    private String bankName;
    
    @CsvBindByName(column = "ref_no", required = false)
    private String utr;
    
    @CsvBindByName(column = "accountNo", required = false)
    private String accountNo;
    
    @CsvBindByName(column = "credit", required = false)
    private BigDecimal creditAmount;
    
    @CsvBindByName(column = "txn_date", required = false)
    @CsvDate("dd-MM-yyyy")
    private LocalDate transactionDate;
    
    @CsvBindByName(column = "narration", required = false)
    private String narration;

    public String getUtr() {
        if (narration != null) {
            String[] parts = narration.split("[-/\\\\s]+");
            for (String part : parts) {
                if (part.startsWith("YESBR") || part.startsWith("UTR") || part.startsWith("PPREF")) {
                    return part;
                }
            }
        }
        return utr;
    }
}
