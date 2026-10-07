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
@org.springframework.security.test.context.support.WithMockUser(roles = "ANALYST")
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
        redisTemplate.delete("rate:upload:system");
        redisTemplate.delete("rate:upload:system:ts");
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete("upload_rate_limit:system");
        redisTemplate.delete("rate:upload:system");
        redisTemplate.delete("rate:upload:system:ts");
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
        String csv = "entity_id,order_id,amount,fee\npay_missing,order_1,500,10";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", MediaType.TEXT_PLAIN_VALUE, csv.getBytes());

        mockMvc.perform(multipart("/api/v1/upload")
                .file(file)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid CSV format. Missing required columns for RAZORPAY")));
    }

    @Test
    void testWrongGatewayHeuristic_Returns400() throws Exception {
        // User selects Razorpay, but uploads a PhonePe file
        String phonePeCsv = "PhonePeReferenceId,MerchantReferenceId,Amount,SettlementDate\nTXN_HEURISTIC1,M1,500,2026";
        MockMultipartFile file1 = new MockMultipartFile("file", "test.csv", MediaType.TEXT_PLAIN_VALUE, phonePeCsv.getBytes());

        mockMvc.perform(multipart("/api/v1/upload")
                .file(file1)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid gateway format. Expected: RAZORPAY but detected structure for PHONEPE.")));
                
        // User selects PhonePe, but uploads a PayU file
        redisTemplate.delete("rate:upload:user");
        String payUCsv = "paymentid,orderid,grossamount,settlementutr\nPAYU_HEURISTIC2,O1,500,UTR1";
        MockMultipartFile file2 = new MockMultipartFile("file", "test2.csv", MediaType.TEXT_PLAIN_VALUE, payUCsv.getBytes());

        mockMvc.perform(multipart("/api/v1/upload")
                .file(file2)
                .param("sourceType", "GATEWAY")
                .param("gateway", "PHONEPE")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid gateway format. Expected: PHONEPE but detected structure for PAYU.")));
    }

    @Test
    void testCorrectGatewayFiles_Returns200() throws Exception {
        // Razorpay
        String razorpayCsv = "entity_id,order_id,amount,settlement_utr\npay_correct_rzp,order_1,500,utr_1";
        MockMultipartFile rzpFile = new MockMultipartFile("file", "rzp.csv", MediaType.TEXT_PLAIN_VALUE, razorpayCsv.getBytes());
        mockMvc.perform(multipart("/api/v1/upload")
                .file(rzpFile)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isOk());

        // PhonePe
        redisTemplate.delete("rate:upload:user");
        String phonePeCsv = "PhonePeReferenceId,MerchantReferenceId,Amount,SettlementDate\nTXN_CORRECT_PP,M1,500,2026";
        MockMultipartFile ppFile = new MockMultipartFile("file", "pp.csv", MediaType.TEXT_PLAIN_VALUE, phonePeCsv.getBytes());
        mockMvc.perform(multipart("/api/v1/upload")
                .file(ppFile)
                .param("sourceType", "GATEWAY")
                .param("gateway", "PHONEPE")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isOk());

        // PayU
        redisTemplate.delete("rate:upload:user");
        String payUCsv = "paymentid,orderid,grossamount,settlementutr\nPAYU_CORRECT,O1,500,UTR1";
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
        String csv = "entity_id,order_id,amount,settlement_utr\npay_traversal,order_1,500,utr_1";
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.csv", MediaType.TEXT_PLAIN_VALUE, csv.getBytes());

        mockMvc.perform(multipart("/api/v1/upload")
                .file(file)
                .param("sourceType", "GATEWAY")
                .param("gateway", "RAZORPAY")
                .param("settlementDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid file name. Please rename your file and try again.")));
    }

    @Test
    void testRateLimiting_Returns429() throws Exception {
        boolean hitRateLimit = false;
        
        for (int i = 0; i < 15; i++) {
            String csv = "entity_id,order_id,amount,settlement_utr\npay_rate_limit_" + i + ",order_1,500,utr_1";
            MockMultipartFile file = new MockMultipartFile("file", "test" + i + ".csv", MediaType.TEXT_PLAIN_VALUE, csv.getBytes());

            int status = mockMvc.perform(multipart("/api/v1/upload")
                    .file(file)
                    .param("sourceType", "GATEWAY")
                    .param("gateway", "RAZORPAY")
                    .param("settlementDate", "2026-10-01"))
                    .andReturn().getResponse().getStatus();
                    
            if (status == 429) {
                hitRateLimit = true;
                break;
            }
        }
        
        org.junit.jupiter.api.Assertions.assertTrue(hitRateLimit, "Rate limit should have been hit when sending 15 sequential requests");
    }

    @Test
    void testBomRemoval() throws Exception {
        // Create CSV with UTF-8 BOM
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = "entity_id,order_id,amount,settlement_utr\npay_bom,order_1,500,utr_1".getBytes();
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
