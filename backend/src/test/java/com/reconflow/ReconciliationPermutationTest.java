package com.reconflow;

import com.reconflow.entity.LedgerTransaction;
import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.GatewaySettlementRepository;
import com.reconflow.repository.LedgerTransactionRepository;
import com.reconflow.repository.UploadedFileRepository;
import com.reconflow.repository.ChainAMatchRepository;
import com.reconflow.service.SqsJobListenerService;
import com.reconflow.batch.DependencySweeperTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.core.io.ByteArrayResource;
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
public class ReconciliationPermutationTest {

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
    private ChainAMatchRepository chainAMatchRepo;

    @Autowired
    private SqsJobListenerService sqsJobListenerService;

    @Autowired
    private DependencySweeperTask dependencySweeperTask;

    @Autowired
    private S3Client s3Client;

    private String bucketName = "reconflow-uploads-test";
    private List<String> s3KeysToDelete = new ArrayList<>();
    
    private String orderId;
    private String paymentId;
    private String utr;

    @BeforeEach
    void setup() {
        chainAMatchRepo.deleteAll();
        exceptionRepo.deleteAll();
        ledgerRepo.deleteAll();
        settlementRepo.deleteAll();
        fileRepo.deleteAll();
        redisTemplate.delete("upload_rate_limit:LEDGER");
        redisTemplate.delete("upload_rate_limit:GATEWAY");
        redisTemplate.delete("upload_rate_limit:BANK");
        
        orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 8);
        utr = "UTR-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void tearDown() {
        chainAMatchRepo.deleteAll();
        exceptionRepo.deleteAll();
        ledgerRepo.deleteAll();
        settlementRepo.deleteAll();
        fileRepo.deleteAll();
        redisTemplate.delete("upload_rate_limit:LEDGER");
        redisTemplate.delete("upload_rate_limit:GATEWAY");
        redisTemplate.delete("upload_rate_limit:BANK");

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

    private void processQueue(int times) throws InterruptedException {
        for (int i = 0; i < times; i++) {
            sqsJobListenerService.pollMainQueue();
            dependencySweeperTask.sweep();
            Thread.sleep(1500);
        }
    }

    private void waitForReconciliation() throws InterruptedException {
        for (int i = 0; i < 15; i++) {
            sqsJobListenerService.pollMainQueue();
            dependencySweeperTask.sweep();
            
            long reconciledCount = ledgerRepo.findAll().stream()
                .filter(l -> "RECONCILED".equals(l.getStatus()))
                .count();
                
            if (reconciledCount > 0) {
                return;
            }
            Thread.sleep(1500);
        }
    }

    private void uploadFile(String type, String gateway, String bank, String csvContent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileAsResource = new ByteArrayResource(csvContent.getBytes()) {
            @Override
            public String getFilename() {
                return "test-" + type + "-" + System.currentTimeMillis() + ".csv";
            }
        };

        body.add("file", fileAsResource);
        body.add("sourceType", type);
        body.add("settlementDate", "2026-10-01");
        
        if (gateway != null) body.add("gateway", gateway);
        if (bank != null) body.add("bankName", bank);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/upload", new HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);

        UUID fileId = UUID.fromString((String) response.getBody().get("fileId"));
        UploadedFile uploadedFile = fileRepo.findById(fileId).orElseThrow();
        
        if (!"DIRECT_DB_UPLOAD".equals(uploadedFile.getS3Key())) {
            s3KeysToDelete.add(uploadedFile.getS3Key());
        }
    }

    private String getLedgerCsv() {
        return "internal_order_id,gateway_transaction_id,amount,currency,gateway,transaction_date\n" +
               orderId + "," + paymentId + ",50000,INR,RAZORPAY,2026-10-01\n";
    }

    private String getGatewayCsv() {
        return "entity_id,order_id,amount,fee,tax,settlement_utr,settlement_id,currency\n" +
               paymentId + "," + orderId + ",50000,0,0," + utr + ",SETT-1,INR\n";
    }

    private String getBankCsv() {
        return "txn_date,narration,deposit amt.,credit,balance,ref_no\n" +
               "01-10-2026,Razorpay Settlement " + utr + ",500.00,500.00,1000.00," + utr + "\n";
    }

    private void verifySuccess() {
        // Assert Ledger is Reconciled
        assertThat(ledgerRepo.findAll()).hasSize(1);
        LedgerTransaction txn = ledgerRepo.findAll().get(0);
        assertThat(txn.getStatus()).isEqualTo("RECONCILED");

        // Assert no exceptions
        assertThat(exceptionRepo.findAll()).isEmpty();
    }

    @Test
    void testOrder_Ledger_Gateway_Bank() throws InterruptedException {
        uploadFile("LEDGER", "RAZORPAY", null, getLedgerCsv());
        uploadFile("GATEWAY", "RAZORPAY", null, getGatewayCsv());
        uploadFile("BANK", null, "SBI", getBankCsv());


        waitForReconciliation();
        verifySuccess();
    }

    @Test
    void testOrder_Ledger_Bank_Gateway() throws InterruptedException {
        uploadFile("LEDGER", "RAZORPAY", null, getLedgerCsv());
        uploadFile("BANK", null, "SBI", getBankCsv());
        processQueue(1); // Bank should delay
        uploadFile("GATEWAY", "RAZORPAY", null, getGatewayCsv());
        

        waitForReconciliation();

        verifySuccess();
    }

    @Test
    void testOrder_Gateway_Ledger_Bank() throws InterruptedException {
        uploadFile("GATEWAY", "RAZORPAY", null, getGatewayCsv());
        processQueue(1); // Gateway should delay
        uploadFile("LEDGER", "RAZORPAY", null, getLedgerCsv());
        uploadFile("BANK", null, "SBI", getBankCsv());
        

        waitForReconciliation();

        verifySuccess();
    }

    @Test
    void testOrder_Gateway_Bank_Ledger() throws InterruptedException {
        uploadFile("GATEWAY", "RAZORPAY", null, getGatewayCsv());
        uploadFile("BANK", null, "SBI", getBankCsv());
        processQueue(2); // Both should delay
        uploadFile("LEDGER", "RAZORPAY", null, getLedgerCsv());
        

        processQueue(1); // Processes Gateway (which allows Bank)
        
 // Sweeps Bank
        waitForReconciliation();

        verifySuccess();
    }

    @Test
    void testOrder_Bank_Ledger_Gateway() throws InterruptedException {
        uploadFile("BANK", null, "SBI", getBankCsv());
        processQueue(1); // Bank should delay
        uploadFile("LEDGER", "RAZORPAY", null, getLedgerCsv());
        uploadFile("GATEWAY", "RAZORPAY", null, getGatewayCsv());
        

        waitForReconciliation();

        verifySuccess();
    }

    @Test
    void testOrder_Bank_Gateway_Ledger() throws InterruptedException {
        uploadFile("BANK", null, "SBI", getBankCsv());
        uploadFile("GATEWAY", "RAZORPAY", null, getGatewayCsv());
        processQueue(2); // Both should delay
        uploadFile("LEDGER", "RAZORPAY", null, getLedgerCsv());
        

        processQueue(1); // Processes Gateway (which allows Bank)
        
        dependencySweeperTask.sweep(); // Sweeps Bank
        waitForReconciliation();

        verifySuccess();
    }
}
