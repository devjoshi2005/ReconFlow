package com.reconflow;

import com.reconflow.entity.UploadedFile;
import com.reconflow.entity.status.UploadedFileStatus;
import com.reconflow.repository.UploadedFileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"scheduling.enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
public class UploadE2ETest extends com.reconflow.support.IntegrationTestSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private SqsClient sqsClient;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    private UUID uploadedFileId;
    private UUID ledgerPrerequisiteFileId;
    private String s3KeyToDelete;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        redisTemplate.delete("rate:upload:system");
        redisTemplate.delete("rate:upload:system:ts");
    }

    @AfterEach
    void tearDown() {
        if (uploadedFileId != null) {
            uploadedFileRepository.deleteById(uploadedFileId);
        }
        if (ledgerPrerequisiteFileId != null) {
            uploadedFileRepository.deleteById(ledgerPrerequisiteFileId);
        }
        if (s3KeyToDelete != null) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3KeyToDelete)
                    .build());
        }
        redisTemplate.delete("rate:upload:system");
        redisTemplate.delete("rate:upload:system:ts");
    }

    @Autowired
    private com.reconflow.service.SqsJobListenerService sqsJobListenerService;

    private String getAuthCookie() {
        Map<String, String> loginReq = Map.of("username", "admin", "password", "admin");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        return cookies != null && !cookies.isEmpty() ? cookies.get(0) : "";
    }

    @Test
    void testEndToEndUploadFlow() {
        // 1. Prepare HTTP request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        String authCookie = getAuthCookie();
        headers.add(HttpHeaders.COOKIE, authCookie);

        // A GATEWAY file only reconciles after a LEDGER file for the same settlement
        // date has reached COMPLETED. Seeding that prerequisite makes this a real
        // end-to-end test of the happy path rather than the dependency-deferral path.
        UploadedFile ledgerPrerequisite = uploadedFileRepository.save(UploadedFile.builder()
                .fileName("e2e-ledger-prerequisite.csv")
                .s3Key("DIRECT_DB_PREREQUISITE")
                .fileChecksum("e2e-ledger-" + UUID.randomUUID())
                .sourceType("LEDGER")
                .gateway("PHONEPE")
                .settlementDate(LocalDate.parse("2026-09-28"))
                .status(UploadedFileStatus.COMPLETED)
                .build());
        ledgerPrerequisiteFileId = ledgerPrerequisite.getId();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        String testContent = "PhonePeReferenceId,MerchantReferenceId,Amount,SettlementDate\nTXN-001,merch-1,500.00,2026-09-28\n" + System.currentTimeMillis();
        
        ByteArrayResource fileAsResource = new ByteArrayResource(testContent.getBytes()) {
            @Override
            public String getFilename() {
                return "e2e-test-" + System.currentTimeMillis() + ".csv";
            }
        };

        body.add("file", fileAsResource);
        body.add("sourceType", "GATEWAY");
        body.add("gateway", "PHONEPE");
        body.add("settlementDate", "2026-09-28");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 2. Execute API Call
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/upload", requestEntity, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        String fileIdStr = (String) response.getBody().get("fileId");
        uploadedFileId = UUID.fromString(fileIdStr);

        // 3. Verify Database
        UploadedFile dbRecord = uploadedFileRepository.findById(uploadedFileId).orElseThrow();
        assertThat(dbRecord.getGateway()).isEqualTo("PHONEPE");
        assertThat(dbRecord.getStatus()).isEqualTo(com.reconflow.entity.status.UploadedFileStatus.QUEUED);
        
        s3KeyToDelete = dbRecord.getS3Key();

        // 4. Verify S3 (Check if object exists)
        HeadObjectResponse headObject = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(s3KeyToDelete)
                .build());
        assertThat(headObject.contentLength()).isEqualTo(testContent.getBytes().length);

        // 5. Verify the listener runs the gateway job and reaches COMPLETED
        UploadedFileStatus finalStatus = null;
        for (int i = 0; i < 15; i++) {
            sqsJobListenerService.pollMainQueue(); // Manually poll SQS because scheduling is disabled

            UploadedFile f = uploadedFileRepository.findById(uploadedFileId).orElseThrow();
            finalStatus = f.getStatus();
            if (finalStatus == UploadedFileStatus.COMPLETED || finalStatus == UploadedFileStatus.FAILED) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        assertThat(finalStatus)
                .as("GATEWAY file should reconcile to COMPLETED once the LEDGER prerequisite exists")
                .isEqualTo(UploadedFileStatus.COMPLETED);
    }
}
