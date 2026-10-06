package com.reconflow;

import com.reconflow.entity.LedgerTransaction;
import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.GatewaySettlementRepository;
import com.reconflow.repository.LedgerTransactionRepository;
import com.reconflow.repository.UploadedFileRepository;
import com.reconflow.repository.ChainAMatchRepository;
import com.reconflow.service.SqsJobListenerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"scheduling.enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
public class ReconciliationE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LedgerTransactionRepository ledgerRepo;

    @Autowired
    private GatewaySettlementRepository settlementRepo;

    @Autowired
    private ExceptionQueueRepository exceptionRepo;

    @Autowired
    private UploadedFileRepository fileRepo;

    @Autowired
    private ChainAMatchRepository chainAMatchRepo;

    @Autowired
    private SqsJobListenerService sqsJobListenerService;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private SqsClient sqsClient;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private UUID testLedgerId;
    private UUID testFileId;
    private String s3KeyToDelete;
    private final String testTxnId = "E2E-TXN-" + System.currentTimeMillis();

    @BeforeEach
    void setup() {
        chainAMatchRepo.deleteAll();
        exceptionRepo.deleteAll();
        ledgerRepo.deleteAll();
        settlementRepo.deleteAll();
        fileRepo.deleteAll();
        
        // Seed the ledger with a pending transaction
        LedgerTransaction txn = LedgerTransaction.builder()
                .internalOrderId("ORDER-1")
                .gatewayTransactionId(testTxnId)
                .gateway("PHONEPE")
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .status("UNRECONCILED")
                .transactionDate(ZonedDateTime.now())
                .build();
        testLedgerId = ledgerRepo.save(txn).getId();

        // Seed a dummy LEDGER UploadedFile so the Gateway delay logic doesn't block
        UploadedFile dummyLedgerFile = UploadedFile.builder()
                .fileName("dummy-ledger.csv")
                .s3Key("DIRECT_DB_UPLOAD")
                .fileChecksum("dummy-checksum-" + System.currentTimeMillis())
                .sourceType("LEDGER")
                .gateway("PHONEPE")
                .settlementDate(java.time.LocalDate.parse("2026-09-28"))
                .status("COMPLETED")
                .build();
        fileRepo.save(dummyLedgerFile);
        
        UploadedFile dummyLedgerFileRzp = UploadedFile.builder()
                .fileName("dummy-ledger-rzp.csv")
                .s3Key("DIRECT_DB_UPLOAD")
                .fileChecksum("dummy-checksum-rzp-" + System.currentTimeMillis())
                .sourceType("LEDGER")
                .gateway("RAZORPAY")
                .settlementDate(java.time.LocalDate.parse("2026-09-28"))
                .status("COMPLETED")
                .build();
        fileRepo.save(dummyLedgerFileRzp);
        
        UploadedFile dummyLedgerFilePayu = UploadedFile.builder()
                .fileName("dummy-ledger-payu.csv")
                .s3Key("DIRECT_DB_UPLOAD")
                .fileChecksum("dummy-checksum-payu-" + System.currentTimeMillis())
                .sourceType("LEDGER")
                .gateway("PAYU")
                .settlementDate(java.time.LocalDate.parse("2026-09-28"))
                .status("COMPLETED")
                .build();
        fileRepo.save(dummyLedgerFilePayu);
    }

    @AfterEach
    void tearDown() {
        // Clean up everything to leave the DB untouched
        chainAMatchRepo.deleteAll();
        exceptionRepo.deleteAll();
        ledgerRepo.deleteAll();
        settlementRepo.deleteAll();
        fileRepo.deleteAll();
        
        if (s3KeyToDelete != null) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3KeyToDelete)
                        .build());
            } catch (Exception e) {}
        }
    }

    @Test
    void testFullReconciliationPipeline_PhonePe() throws InterruptedException {
        // Seed Ledger with 2 transactions
        LedgerTransaction txn1 = LedgerTransaction.builder()
                .internalOrderId("ORDER-PP-1")
                .gatewayTransactionId("pay_PP1")
                .gateway("PHONEPE")
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .status("UNRECONCILED")
                .transactionDate(ZonedDateTime.now())
                .build();
        
        LedgerTransaction txn2 = LedgerTransaction.builder()
                .internalOrderId("ORDER-PP-2")
                .gatewayTransactionId("pay_PP2")
                .gateway("PHONEPE")
                .amount(new BigDecimal("300.00"))
                .currency("INR")
                .status("UNRECONCILED")
                .transactionDate(ZonedDateTime.now())
                .build();

        UUID ledgerId1 = ledgerRepo.save(txn1).getId();
        UUID ledgerId2 = ledgerRepo.save(txn2).getId();

        // 1. Prepare Mock CSV
        // Row 1: Correct Match (pay_PP1)
        // Row 2: Amount Mismatch (pay_PP2 is 290 instead of 300)
        // Row 3: Missing in Ledger (pay_PP3 does not exist in DB)
        // Row 4: Malicious injection (starts with =)
        String csvContent = "phonepereferenceid,merchantreferenceid,amount,fee,igst,cgst,sgst,bankreferenceno,settlementdate\n" +
                "pay_PP1,ORDER-PP-1,500.00,0.00,0.00,0.00,0.00,UTR_PP1,01/10/2026\n" +
                "pay_PP2,ORDER-PP-2,290.00,0.00,0.00,0.00,0.00,UTR_PP2,01/10/2026\n" +
                "pay_PP3,ORDER-PP-3,100.00,0.00,0.00,0.00,0.00,UTR_PP3,01/10/2026\n" +
                "=cmd|'/C calc'!A0,ORDER-MAL,100.00,0.00,0.00,0.00,0.00,UTR_MAL,01/10/2026";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileAsResource = new ByteArrayResource(csvContent.getBytes()) {
            @Override
            public String getFilename() {
                return "e2e-phonepe-" + System.currentTimeMillis() + ".csv";
            }
        };

        body.add("file", fileAsResource);
        body.add("sourceType", "GATEWAY");
        body.add("gateway", "PHONEPE");
        body.add("settlementDate", "2026-09-28");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 2. Upload via API
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/upload", requestEntity, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        testFileId = UUID.fromString((String) response.getBody().get("fileId"));
        s3KeyToDelete = fileRepo.findById(testFileId).get().getS3Key();

        // 3. Wait for SQS listener and Batch Job to complete
        boolean isCompleted = false;
        for (int i = 0; i < 45; i++) {
            sqsJobListenerService.pollMainQueue();
            Thread.sleep(2000);
            UploadedFile updatedFile = fileRepo.findById(testFileId).orElseThrow();
            if ("COMPLETED".equals(updatedFile.getStatus())) {
                isCompleted = true;
                break;
            } else if ("FAILED".equals(updatedFile.getStatus())) {
                System.err.println("Batch Job FAILED! Check logs. Error: " + updatedFile.getErrorMessage());
                break;
            }
        }
        
        assertThat(isCompleted).as("Job should eventually complete").isTrue();

        // 4. Verification Step!
        UploadedFile updatedFile = fileRepo.findById(testFileId).orElseThrow();
        assertThat(updatedFile.getStatus()).isEqualTo("COMPLETED");

        // 4.1 Match Success Check
        LedgerTransaction matchedLedger = ledgerRepo.findById(ledgerId1).orElseThrow();
        assertThat(matchedLedger.getStatus()).isEqualTo("RECONCILED");

        // 4.2 Amount Mismatch Check
        LedgerTransaction mismatchLedger = ledgerRepo.findById(ledgerId2).orElseThrow();
        assertThat(mismatchLedger.getStatus()).isEqualTo("UNRECONCILED"); // Unchanged since it failed to fully reconcile

        // 4.3 Exceptions Check
        long mismatchExceptions = exceptionRepo.findAll().stream()
                .filter(e -> "AMOUNT_MISMATCH".equals(e.getExceptionType()) && e.getDiscrepancyAmount().compareTo(new BigDecimal("10.00")) == 0)
                .count();
        assertThat(mismatchExceptions).isEqualTo(1);

        long missingInLedgerExceptions = exceptionRepo.findAll().stream()
                .filter(e -> "MISSING_IN_LEDGER".equals(e.getExceptionType()))
                .count();
        assertThat(missingInLedgerExceptions).isEqualTo(2); // One for pay_PP3, one for the malicious row (which gets stripped to cmd|...)

        // Cleanup handled by @AfterEach
        System.out.println("FULL PIPELINE SUCCESS (PHONEPE)");
    }

    @Test
    void testFullReconciliationPipeline_Razorpay() throws InterruptedException {
        // Seed Ledger
        LedgerTransaction rzpLedger = LedgerTransaction.builder()
                .internalOrderId("ORDER-RZP-1")
                .gatewayTransactionId("pay_RZP1")
                .gateway("RAZORPAY")
                .amount(new BigDecimal("999.00"))
                .currency("INR")
                .status("UNRECONCILED")
                .transactionDate(ZonedDateTime.now())
                .build();
        
        LedgerTransaction rzpLedger2 = LedgerTransaction.builder()
                .internalOrderId("ORDER-RZP-2")
                .gatewayTransactionId("pay_RZP2")
                .gateway("RAZORPAY")
                .amount(new BigDecimal("200.00"))
                .currency("INR")
                .status("UNRECONCILED")
                .transactionDate(ZonedDateTime.now())
                .build();
                
        UUID ledgerId1 = ledgerRepo.save(rzpLedger).getId();
        UUID ledgerId2 = ledgerRepo.save(rzpLedger2).getId();

        // 1. Prepare Mock CSV
        // Row 1: Correct
        // Row 2: Amount Mismatch (199 instead of 200)
        // Row 3: Missing in Ledger
        String csvContent = "entity_id,order_id,amount,fee,tax,settlement_utr,settlement_id,currency\n" +
                "pay_RZP1,ORDER-RZP-1,99900,0,0,UTR_RZP1,SETT_RZP,INR\n" +
                "pay_RZP2,ORDER-RZP-2,19900,0,0,UTR_RZP2,SETT_RZP,INR\n" +
                "pay_RZP3,ORDER-RZP-3,10000,0,0,UTR_RZP3,SETT_RZP,INR";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileAsResource = new ByteArrayResource(csvContent.getBytes()) {
            @Override
            public String getFilename() {
                return "e2e-rzp-" + System.currentTimeMillis() + ".csv";
            }
        };

        body.add("file", fileAsResource);
        body.add("sourceType", "GATEWAY");
        body.add("gateway", "RAZORPAY");
        body.add("settlementDate", "2026-09-28");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 2. Upload via API
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/upload", requestEntity, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        testFileId = UUID.fromString((String) response.getBody().get("fileId"));
        s3KeyToDelete = fileRepo.findById(testFileId).get().getS3Key();

        // 3. Wait for SQS listener and Batch Job to complete
        boolean isCompleted = false;
        for (int i = 0; i < 45; i++) {
            sqsJobListenerService.pollMainQueue();
            Thread.sleep(2000);
            UploadedFile updatedFile = fileRepo.findById(testFileId).orElseThrow();
            if ("COMPLETED".equals(updatedFile.getStatus())) {
                isCompleted = true;
                break;
            } else if ("FAILED".equals(updatedFile.getStatus())) {
                System.err.println("Batch Job FAILED! Check logs. Error: " + updatedFile.getErrorMessage());
                break;
            }
        }
        
        assertThat(isCompleted).as("Job should eventually complete").isTrue();

        // 4. Verification Step!
        UploadedFile updatedFile = fileRepo.findById(testFileId).orElseThrow();
        assertThat(updatedFile.getStatus()).isEqualTo("COMPLETED");

        LedgerTransaction updatedLedger = ledgerRepo.findById(ledgerId1).orElseThrow();
        assertThat(updatedLedger.getStatus()).isEqualTo("RECONCILED");
        
        LedgerTransaction mismatchLedger = ledgerRepo.findById(ledgerId2).orElseThrow();
        assertThat(mismatchLedger.getStatus()).isEqualTo("UNRECONCILED");

        // Cleanup handled by @AfterEach
        System.out.println("FULL PIPELINE SUCCESS (RAZORPAY)");
    }

    @Test
    void testFullReconciliationPipeline_PayU() throws InterruptedException {
        // Seed Ledger
        LedgerTransaction payuLedger = LedgerTransaction.builder()
                .internalOrderId("ORDER-PAYU")
                .gatewayTransactionId("payu_123")
                .gateway("PAYU")
                .amount(new BigDecimal("1500.00"))
                .currency("INR")
                .status("UNRECONCILED")
                .transactionDate(ZonedDateTime.now())
                .build();
                
        LedgerTransaction payuLedger2 = LedgerTransaction.builder()
                .internalOrderId("ORDER-PAYU-2")
                .gatewayTransactionId("payu_124")
                .gateway("PAYU")
                .amount(new BigDecimal("800.00"))
                .currency("INR")
                .status("UNRECONCILED")
                .transactionDate(ZonedDateTime.now())
                .build();
                
        UUID ledgerId1 = ledgerRepo.save(payuLedger).getId();
        UUID ledgerId2 = ledgerRepo.save(payuLedger2).getId();

        // 1. Prepare Mock CSV
        String csvContent = "PaymentId,OrderId,GrossAmount,MDR,GSTOnMDR,SettlementUTR,SettlementId\n" +
                "payu_123,ORDER-PAYU,1500.00,0.00,0.00,UTR_PAYU,SETT_PAYU\n" +
                "payu_124,ORDER-PAYU-2,700.00,0.00,0.00,UTR_PAYU2,SETT_PAYU\n" +
                "payu_125,ORDER-PAYU-3,100.00,0.00,0.00,UTR_PAYU3,SETT_PAYU";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileAsResource = new ByteArrayResource(csvContent.getBytes()) {
            @Override
            public String getFilename() {
                return "e2e-payu-" + System.currentTimeMillis() + ".csv";
            }
        };

        body.add("file", fileAsResource);
        body.add("sourceType", "GATEWAY");
        body.add("gateway", "PAYU");
        body.add("settlementDate", "2026-09-28");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 2. Upload via API
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/upload", requestEntity, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        testFileId = UUID.fromString((String) response.getBody().get("fileId"));
        s3KeyToDelete = fileRepo.findById(testFileId).get().getS3Key();

        // 3. Wait for SQS listener and Batch Job to complete
        boolean isCompleted = false;
        for (int i = 0; i < 45; i++) {
            sqsJobListenerService.pollMainQueue();
            Thread.sleep(2000);
            UploadedFile updatedFile = fileRepo.findById(testFileId).orElseThrow();
            if ("COMPLETED".equals(updatedFile.getStatus())) {
                isCompleted = true;
                break;
            } else if ("FAILED".equals(updatedFile.getStatus())) {
                System.err.println("Batch Job FAILED! Check logs. Error: " + updatedFile.getErrorMessage());
                break;
            }
        }
        
        assertThat(isCompleted).as("Job should eventually complete").isTrue();

        // 4. Verification Step!
        UploadedFile updatedFile = fileRepo.findById(testFileId).orElseThrow();
        assertThat(updatedFile.getStatus()).isEqualTo("COMPLETED");

        LedgerTransaction updatedLedger = ledgerRepo.findById(ledgerId1).orElseThrow();
        assertThat(updatedLedger.getStatus()).isEqualTo("RECONCILED");
        
        LedgerTransaction mismatchLedger = ledgerRepo.findById(ledgerId2).orElseThrow();
        assertThat(mismatchLedger.getStatus()).isEqualTo("UNRECONCILED");

        // Cleanup handled by @AfterEach
        System.out.println("FULL PIPELINE SUCCESS (PAYU)");
    }
}
