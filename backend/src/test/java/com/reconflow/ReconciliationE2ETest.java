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
import java.util.List;
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
    private com.reconflow.batch.DependencySweeperTask dependencySweeperTask;

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

    private String getAuthCookie() {
        Map<String, String> loginReq = Map.of("email", "admin", "password", "admin");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        return cookies != null && !cookies.isEmpty() ? cookies.get(0) : "";
    }

    private void uploadCsv(String type, String gateway, String bank, String content, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, getAuthCookie());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileAsResource = new ByteArrayResource(content.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        body.add("file", fileAsResource);
        body.add("sourceType", type);
        body.add("settlementDate", "2026-10-02");
        if (gateway != null) body.add("gateway", gateway);
        if (bank != null) body.add("bankName", bank);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/upload", requestEntity, Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);
        
        UUID fileId = UUID.fromString((String) response.getBody().get("fileId"));
        s3KeyToDelete = fileRepo.findById(fileId).get().getS3Key();
    }

    @Test
    void testComprehensive3WayReconciliation() throws InterruptedException {
        // 1. Ledger CSV (5 rows)
        String ledgerCsv = "internal_order_id,gateway_transaction_id,amount,currency,gateway,transaction_date\n" +
                "ORDER-1,pay_1,100.00,INR,RAZORPAY,2026-10-01\n" +
                "ORDER-2,pay_2,200.00,INR,RAZORPAY,2026-10-01\n" +
                "ORDER-3,pay_3,300.00,INR,RAZORPAY,2026-10-01\n" +
                "ORDER-4,pay_4,400.00,INR,RAZORPAY,2026-10-01\n" +
                "ORDER-5,pay_5,500.00,INR,RAZORPAY,2026-10-01";
        
        uploadCsv("LEDGER", "RAZORPAY", null, ledgerCsv, "ledger-5rows.csv");

        // 2. Gateway CSV (5 rows)
        String gatewayCsv = "entity_id,order_id,amount,fee,tax,settlement_utr,settlement_id,currency\n" +
                "pay_1,ORDER-1,10000,0,0,UTR-1,SETT-1,INR\n" +
                "pay_2,ORDER-2,20000,0,0,UTR-1,SETT-1,INR\n" +
                "pay_3,ORDER-3,30000,0,0,UTR-1,SETT-1,INR\n" +
                "pay_4,ORDER-4,40000,0,0,UTR-2,SETT-2,INR\n" +
                "pay_5,ORDER-5,50000,0,0,UTR-2,SETT-2,INR";

        uploadCsv("GATEWAY", "RAZORPAY", null, gatewayCsv, "gateway-5rows.csv");

        // 3. Bank CSV (2 rows)
        String bankCsv = "txn_date,narration,deposit amt.,credit,balance,ref_no\n" +
                "02-10-2026,Razorpay Settlement UTR-1,600.00,600.00,1600.00,UTR-1\n" +
                "02-10-2026,Razorpay Settlement UTR-2,900.00,900.00,2500.00,UTR-2";

        uploadCsv("BANK", null, "SBI", bankCsv, "bank-2rows.csv");

        // 4. Wait for SQS listener and Batch Jobs to process everything
        for (int i = 0; i < 45; i++) {
            sqsJobListenerService.pollMainQueue();
            dependencySweeperTask.sweep();
            
            long reconciledCount = ledgerRepo.findAll().stream()
                .filter(l -> "RECONCILED".equals(l.getStatus()))
                .count();
                
            if (reconciledCount == 5) {
                break;
            }
            Thread.sleep(1500);
        }

        // 5. Assertions - Seeing Calculations
        List<LedgerTransaction> ledgers = ledgerRepo.findAll();
        assertThat(ledgers).hasSize(5);
        for (LedgerTransaction l : ledgers) {
            assertThat(l.getStatus()).isEqualTo("RECONCILED");
        }

        assertThat(exceptionRepo.count()).isEqualTo(0);
        System.out.println("FULL 3-WAY RECONCILIATION SUCCESS (5 Rows Ledger/Gateway, 2 Rows Bank)");
    }
}
