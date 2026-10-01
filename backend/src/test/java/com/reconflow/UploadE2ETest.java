package com.reconflow;

import com.reconflow.entity.UploadedFile;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UploadE2ETest {

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
    private String s3KeyToDelete;

    @AfterEach
    void tearDown() {
        if (uploadedFileId != null) {
            uploadedFileRepository.deleteById(uploadedFileId);
        }
        if (s3KeyToDelete != null) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3KeyToDelete)
                    .build());
        }
    }

    @Test
    void testEndToEndUploadFlow() {
        // 1. Prepare HTTP request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        String testContent = "id,amount,status\nTXN-001,500,SUCCESS\n" + System.currentTimeMillis();
        
        ByteArrayResource fileAsResource = new ByteArrayResource(testContent.getBytes()) {
            @Override
            public String getFilename() {
                return "e2e-test-" + System.currentTimeMillis() + ".csv";
            }
        };

        body.add("file", fileAsResource);
        body.add("sourceType", "GATEWAY");
        body.add("gateway", "PHONEPE");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 2. Execute API Call
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/upload", requestEntity, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        String fileIdStr = (String) response.getBody().get("fileId");
        uploadedFileId = UUID.fromString(fileIdStr);

        // 3. Verify Database
        UploadedFile dbRecord = uploadedFileRepository.findById(uploadedFileId).orElseThrow();
        assertThat(dbRecord.getGateway()).isEqualTo("PHONEPE");
        assertThat(dbRecord.getStatus()).isEqualTo("QUEUED");
        
        s3KeyToDelete = dbRecord.getS3Key();

        // 4. Verify S3 (Check if object exists)
        HeadObjectResponse headObject = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(s3KeyToDelete)
                .build());
        assertThat(headObject.contentLength()).isEqualTo(testContent.getBytes().length);

        // 5. Verify the listener picked it up (status changes from QUEUED)
        boolean processed = false;
        for (int i = 0; i < 15; i++) {
            UploadedFile f = uploadedFileRepository.findById(uploadedFileId).orElseThrow();
            if ("PROCESSING".equals(f.getStatus()) || "COMPLETED".equals(f.getStatus()) || "FAILED".equals(f.getStatus())) {
                processed = true;
                break;
            }
            try { Thread.sleep(2000); } catch (InterruptedException e) {}
        }
        
        assertThat(processed).as("File should be picked up by the listener and status changed from QUEUED").isTrue();
    }
}
