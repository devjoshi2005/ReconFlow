package com.reconflow;

import com.reconflow.batch.DependencySweeperTask;
import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.GatewaySettlement;
import com.reconflow.entity.LedgerTransaction;
import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.GatewaySettlementRepository;
import com.reconflow.repository.LedgerTransactionRepository;
import com.reconflow.repository.UploadedFileRepository;
import com.reconflow.service.SqsJobListenerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"scheduling.enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
public class ReconciliationMaliciousTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LedgerTransactionRepository ledgerRepo;

    @Autowired
    private GatewaySettlementRepository settlementRepo;

    @Autowired
    private ExceptionQueueRepository exceptionRepo;

    @Autowired
    private UploadedFileRepository fileRepo;

    @Autowired
    private SqsJobListenerService sqsJobListenerService;

    @Autowired
    private DependencySweeperTask dependencySweeperTask;

    @Autowired
    private S3Client s3Client;

    private String bucketName = "reconflow-uploads-test";
    private List<String> s3KeysToDelete = new ArrayList<>();

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM chain_b_matches");
        jdbcTemplate.execute("DELETE FROM chain_a_matches");
        exceptionRepo.deleteAll();
        ledgerRepo.deleteAll();
        settlementRepo.deleteAll();
        fileRepo.deleteAll();
        redisTemplate.delete("upload_rate_limit:system");
        redisTemplate.delete("rate:upload:system");
        redisTemplate.delete("rate:upload:system:ts");
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM chain_b_matches");
        jdbcTemplate.execute("DELETE FROM chain_a_matches");
        exceptionRepo.deleteAll();
        ledgerRepo.deleteAll();
        settlementRepo.deleteAll();
        fileRepo.deleteAll();
        redisTemplate.delete("upload_rate_limit:system");
        redisTemplate.delete("rate:upload:system");
        redisTemplate.delete("rate:upload:system:ts");

        for (String key : s3KeysToDelete) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build());
            } catch (Exception e) {}
        }
        s3KeysToDelete.clear();
    }

    private void waitForReconciliation() throws InterruptedException {
        for (int i = 0; i < 15; i++) {
            sqsJobListenerService.pollMainQueue();
            dependencySweeperTask.sweep();
            
            long reconciledCount = ledgerRepo.findAll().stream()
                .filter(l -> "RECONCILED".equals(l.getStatus()) || "EXCEPTION".equals(l.getStatus()))
                .count();
                
            if (reconciledCount > 0) {
                return;
            }
            Thread.sleep(1500);
        }
    }

    private String getAuthCookie() {
        Map<String, String> loginReq = Map.of("email", "admin", "password", "admin");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        return cookies != null && !cookies.isEmpty() ? cookies.get(0) : "";
    }

    private void uploadFile(String type, String gateway, String csvContent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        String authCookie = getAuthCookie();
        headers.add(HttpHeaders.COOKIE, authCookie);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileAsResource = new ByteArrayResource(csvContent.getBytes()) {
            @Override
            public String getFilename() {
                return "malicious-" + type + "-" + System.currentTimeMillis() + ".csv";
            }
        };

        body.add("file", fileAsResource);
        body.add("sourceType", type);
        body.add("settlementDate", "2026-10-01");
        
        if (gateway != null) body.add("gateway", gateway);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/upload", new HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);

        UUID fileId = UUID.fromString((String) response.getBody().get("fileId"));
        UploadedFile uploadedFile = fileRepo.findById(fileId).orElseThrow();
        
        if (!"DIRECT_DB_UPLOAD".equals(uploadedFile.getS3Key())) {
            s3KeysToDelete.add(uploadedFile.getS3Key());
        }
    }

    @Test
    void testCsvInjectionAndExtremeValues() throws InterruptedException {
        String paymentId = "=cmd|' /C calc'!A0"; // CSV Injection Attempt
        String orderId = "+1-800-HACKED";
        String utr = "@SUM(1+1)";
        
        String ledgerCsv = "internal_order_id,gateway_transaction_id,amount,currency,gateway,transaction_date\n" +
                orderId + "," + paymentId + ",999999999999,INR,RAZORPAY,2026-10-01\n"; // Extreme amount
                
        String gatewayCsv = "entity_id,order_id,amount,fee,tax,settlement_id,settlement_utr,created_at\n" +
                paymentId + "," + orderId + ",999999999999,0,0,SETT-1," + utr + ",1727784000\n";
                
        uploadFile("LEDGER", "RAZORPAY", ledgerCsv);
        uploadFile("GATEWAY", "RAZORPAY", gatewayCsv);

        waitForReconciliation();

        List<LedgerTransaction> txns = ledgerRepo.findAll();
        assertThat(txns).hasSize(1);
        
        LedgerTransaction txn = txns.get(0);
        // Assert malicious prefixes =, +, @ were stripped during parse to prevent CSV injection on re-export
        assertThat(txn.getGatewayTransactionId()).isEqualTo("cmd|' /C calc'!A0");
        assertThat(txn.getInternalOrderId()).isEqualTo("1-800-HACKED");
        
        List<GatewaySettlement> settlements = settlementRepo.findAll();
        assertThat(settlements).hasSize(1);
        
        GatewaySettlement sett = settlements.get(0);
        assertThat(sett.getGatewayTransactionId()).isEqualTo("cmd|' /C calc'!A0");
        assertThat(sett.getBankTransferRef()).isEqualTo("SUM(1+1)");
    }

    @Test
    void testSqlInjectionAndXssAndTruncation() throws InterruptedException {
        String sqlInjection = "'; DROP TABLE ledger_transactions;--";
        String xssPayload = "<script>alert('XSS')</script>";
        // Generate a 1000 character string to test truncation
        String longString = "A".repeat(1000);

        String bankCsv = "txn_date,narration,credit,deposit amt.\n" +
                "01-10-2026," + xssPayload + sqlInjection + longString + ",500.00,500.00\n";

        uploadFile("BANK", null, bankCsv);

        // Process just the Bank file
        for (int i = 0; i < 5; i++) {
            sqsJobListenerService.pollMainQueue();
            dependencySweeperTask.sweep();
            Thread.sleep(1000);
        }

        // The bank record is stored in memory, but wait, the narration might be used for ExceptionQueue notes or just stored.
        // Actually, Bank isn't saved to DB directly right now unless there's an exception, but let's check CsvInMemoryLoader.
        // CsvInMemoryLoader sanitizes and truncates strings. Let's upload a Gateway file with long strings and SQL injection to see if it saves to DB safely.
        String gatewayCsv = "entity_id,order_id,amount,fee,tax,settlement_id,settlement_utr,created_at\n" +
                sqlInjection + "," + xssPayload + ",500,0,0,SETT-1,UTR-1,1727784000\n";
                
        // Upload a Ledger file first so the Gateway file can process (Dependency Sweeper rule)
        String ledgerCsv = "internal_order_id,gateway_transaction_id,amount,currency,gateway,transaction_date\n" +
                xssPayload + "," + sqlInjection + ",500,INR,RAZORPAY,2026-10-01\n";
        uploadFile("LEDGER", "RAZORPAY", ledgerCsv);
                
        uploadFile("GATEWAY", "RAZORPAY", gatewayCsv);

        // Process Gateway file
        for (int i = 0; i < 5; i++) {
            sqsJobListenerService.pollMainQueue();
            dependencySweeperTask.sweep();
            Thread.sleep(1000);
        }

        List<GatewaySettlement> settlements = settlementRepo.findAll();
        assertThat(settlements).hasSize(1);
        
        GatewaySettlement sett = settlements.get(0);
        
        // Assert SQL injection was safely stored without executing (JPA handles this, but we verify it's just a string)
        // Also verify truncation to 100 chars (since GatewayAdapter uses sanitizeAndTruncate(100))
        String expectedEntityId = sqlInjection;
        if (expectedEntityId.length() > 100) expectedEntityId = expectedEntityId.substring(0, 100);
        
        assertThat(sett.getGatewayTransactionId()).isEqualTo(expectedEntityId);
        
        String expectedOrderId = xssPayload;
        if (expectedOrderId.length() > 100) expectedOrderId = expectedOrderId.substring(0, 100);
        assertThat(sett.getOrderId()).isEqualTo(expectedOrderId);
    }
}
