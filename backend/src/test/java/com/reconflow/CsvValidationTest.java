package com.reconflow;

import com.reconflow.service.UploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {"scheduling.enabled=false"})
@AutoConfigureMockMvc
public class CsvValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UploadService uploadService;

    @BeforeEach
    void setup() {
        redisTemplate.delete("upload_rate_limit:system");
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("upload_rate_limit:system");
    }

    @Test
    void testEmptyFile_Returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", MediaType.TEXT_PLAIN_VALUE, "".getBytes());

        mockMvc.perform(multipart("/api/v1/upload")
                .file(file)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("File is empty or missing headers")));
    }

    @Test
    void testMissingRequiredColumn_Returns400() throws Exception {
        // Missing settlement_utr for Razorpay
        String csv = "entity_id,order_id,amount,fee\npay_1,order_1,500,10";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", MediaType.TEXT_PLAIN_VALUE, csv.getBytes());

        mockMvc.perform(multipart("/api/v1/upload")
                .file(file)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Missing required column: 'settlement_utr'")));
    }

    @Test
    void testWrongGatewayHeuristic_Returns400() throws Exception {
        // User selects Razorpay, but uploads a PhonePe file
        String phonePeCsv = "PhonePeReferenceId,MerchantReferenceId,Amount,SettlementDate\nTXN1,M1,500,2026";
        MockMultipartFile file1 = new MockMultipartFile("file", "test.csv", MediaType.TEXT_PLAIN_VALUE, phonePeCsv.getBytes());

        mockMvc.perform(multipart("/api/v1/upload")
                .file(file1)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("This file looks like a PHONEPE file")));
                
        // User selects PhonePe, but uploads a PayU file
        String payUCsv = "paymentid,orderid,grossamount,settlementutr\nPAYU1,O1,500,UTR1";
        MockMultipartFile file2 = new MockMultipartFile("file", "test2.csv", MediaType.TEXT_PLAIN_VALUE, payUCsv.getBytes());

        mockMvc.perform(multipart("/api/v1/upload")
                .file(file2)
                .param("sourceType", "GATEWAY")
                .param("gateway", "PHONEPE")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("This file looks like a PAYU file")));
    }

    @Test
    void testCorrectGatewayFiles_Returns200() throws Exception {
        // Razorpay
        String razorpayCsv = "entity_id,order_id,amount,settlement_utr\npay_1,order_1,500,utr_1";
        MockMultipartFile rzpFile = new MockMultipartFile("file", "rzp.csv", MediaType.TEXT_PLAIN_VALUE, razorpayCsv.getBytes());
        mockMvc.perform(multipart("/api/v1/upload")
                .file(rzpFile)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isOk());

        // PhonePe
        String phonePeCsv = "PhonePeReferenceId,MerchantReferenceId,Amount,SettlementDate\nTXN1,M1,500,2026";
        MockMultipartFile ppFile = new MockMultipartFile("file", "pp.csv", MediaType.TEXT_PLAIN_VALUE, phonePeCsv.getBytes());
        mockMvc.perform(multipart("/api/v1/upload")
                .file(ppFile)
                .param("sourceType", "GATEWAY")
                .param("gateway", "PHONEPE")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isOk());

        // PayU
        String payUCsv = "paymentid,orderid,grossamount,settlementutr\nPAYU1,O1,500,UTR1";
        MockMultipartFile puFile = new MockMultipartFile("file", "pu.csv", MediaType.TEXT_PLAIN_VALUE, payUCsv.getBytes());
        mockMvc.perform(multipart("/api/v1/upload")
                .file(puFile)
                .param("sourceType", "GATEWAY")
                .param("gateway", "PAYU")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isOk());
    }
    @Test
    void testPathTraversal_Returns400() throws Exception {
        String csv = "entity_id,order_id,amount,settlement_utr\npay_1,order_1,500,utr_1";
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.csv", MediaType.TEXT_PLAIN_VALUE, csv.getBytes());

        mockMvc.perform(multipart("/api/v1/upload")
                .file(file)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Path traversal attempt detected")));
    }

    @Test
    void testRateLimiting_Returns429() throws Exception {
        String csv = "entity_id,order_id,amount,settlement_utr\npay_1,order_1,500,utr_1";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", MediaType.TEXT_PLAIN_VALUE, csv.getBytes());

        // Exhaust rate limit (capacity is 10)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(multipart("/api/v1/upload")
                    .file(file)
                    .param("sourceType", "GATEWAY")
                    .param("gateway", "RAZORPAY")
                    .param("settlementDate", "2026-10-01"))
                    .andExpect(status().isOk());
        }

        // 11th request should fail
        mockMvc.perform(multipart("/api/v1/upload")
                .file(file)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rate limit exceeded")));
    }
    @Test
    void testBomRemoval() throws Exception {
        // Create CSV with UTF-8 BOM
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = "entity_id,order_id,amount,settlement_utr\npay_1,order_1,500,utr_1".getBytes();
        byte[] fullContent = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, fullContent, 0, bom.length);
        System.arraycopy(content, 0, fullContent, bom.length, content.length);

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", MediaType.TEXT_PLAIN_VALUE, fullContent);

        mockMvc.perform(multipart("/api/v1/upload")
                .file(file)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isOk());
    }
}
