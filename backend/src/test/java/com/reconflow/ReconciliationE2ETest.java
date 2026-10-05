package com.reconflow;

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
    }

    @AfterEach
    void tearDown() {
        // Clean up everything to leave the DB untouched
        if (testLedgerId != null) {
            ledgerRepo.deleteById(testLedgerId);
        }
        if (testFileId != null) {
            settlementRepo.deleteAll();
            fileRepo.deleteById(testFileId);
        }
        if (s3KeyToDelete != null) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3KeyToDelete)
                    .build());
        }
    }

    @Test
    void testFullReconciliationPipeline() throws InterruptedException {
        // 1. Prepare Mock CSV
        String csvContent = "phonepereferenceid,merchantreferenceid,amount,fee,igst,cgst,sgst,bankreferenceno,creationdate\n" +
                testTxnId + ",ORDER-1,500.00,0.00,0.00,0.00,0.00,UTR_123,01/10/2026";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileAsResource = new ByteArrayResource(csvContent.getBytes()) {
            @Override
            public String getFilename() {
                return "e2e-recon-" + System.currentTimeMillis() + ".csv";
            }
        };

        body.add("file", fileAsResource);
        body.add("sourceType", "GATEWAY");
        body.add("gateway", "PHONEPE"); // Crucial so it routes to the correct reader
        body.add("settlementDate", "2026-09-28");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 2. Upload via API (Uploads to S3, writes DB, pushes to SQS)
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/upload", requestEntity, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        testFileId = UUID.fromString((String) response.getBody().get("fileId"));
        s3KeyToDelete = fileRepo.findById(testFileId).get().getS3Key();

        // 3. Wait for SQS listener and Batch Job to complete
        boolean isCompleted = false;
        for (int i = 0; i < 45; i++) {
            // Manually process jobs in the queue to avoid background thread race conditions
            sqsJobListenerService.pollMainQueue();
            
            Thread.sleep(2000); // Wait 2s
            
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
        // 4.1 Check UploadedFile status
        UploadedFile updatedFile = fileRepo.findById(testFileId).orElseThrow();
        assertThat(updatedFile.getStatus()).isEqualTo("COMPLETED");

        // 4.2 Check LedgerTransaction status
        LedgerTransaction updatedLedger = ledgerRepo.findById(testLedgerId).orElseThrow();
        assertThat(updatedLedger.getStatus()).isEqualTo("RECONCILED");

        System.out.println("FULL PIPELINE SUCCESS: File uploaded -> S3 saved -> SQS queued -> SQS polled -> Batch Job ran -> Postgres updated!");
    }
}
