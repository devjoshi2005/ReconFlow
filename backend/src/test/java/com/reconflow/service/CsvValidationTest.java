package com.reconflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CsvValidationTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        // Mock Redis rate limiter to always allow
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(Collections.singletonList(1L));
    }

    @Test
    void testEmptyFile_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", new byte[0]);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            uploadService.processUpload(file, "GATEWAY", "RAZORPAY", null, java.time.LocalDate.now(), "0.01", "10000000");
        });
        assertTrue(exception.getMessage().contains("empty or missing headers"));
    }

    @Test
    void testPhonePeFileInRazorpaySlot_ThrowsException() {
        // PhonePe columns: phonepereferenceid, merchantreferenceid, amount, settlementdate
        String phonePeCsv = "PhonePeReferenceId,MerchantReferenceId,Amount,SettlementDate\npp_1,mer_1,100,2026-10-01";
        MockMultipartFile file = new MockMultipartFile("file", "phonepe.csv", "text/csv", phonePeCsv.getBytes());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            uploadService.processUpload(file, "GATEWAY", "RAZORPAY", null, java.time.LocalDate.now(), "0.01", "10000000");
        });
        assertTrue(exception.getMessage().contains("Missing required column"));
    }

    @Test
    void testPayUFileInPhonePeSlot_ThrowsException() {
        // PayU columns: paymentid, orderid, grossamount, settlementutr
        String payUCsv = "paymentid,orderid,grossamount,settlementutr\npayu_1,order_1,100,utr_1";
        MockMultipartFile file = new MockMultipartFile("file", "payu.csv", "text/csv", payUCsv.getBytes());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            uploadService.processUpload(file, "GATEWAY", "PHONEPE", null, java.time.LocalDate.now(), "0.01", "10000000");
        });
        assertTrue(exception.getMessage().contains("Missing required column"));
    }
    
    @Test
    void testWrongBankStatement_ThrowsException() {
        // Missing txn_date and credit
        String invalidBankCsv = "Date,Description,Amount\n2026-10-01,test,100";
        MockMultipartFile file = new MockMultipartFile("file", "bank.csv", "text/csv", invalidBankCsv.getBytes());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            uploadService.processUpload(file, "BANK", null, "HDFC", java.time.LocalDate.now(), "0.01", "10000000");
        });
        assertTrue(exception.getMessage().contains("Wrong bank statement file"));
    }
}
