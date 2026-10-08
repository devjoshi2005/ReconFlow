package com.reconflow.controller;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"scheduling.enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
public class UploadControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    private UUID uploadedFileId;
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        if (uploadedFileId != null) {
            uploadedFileRepository.deleteById(uploadedFileId);
        }
        redisTemplate.delete("rate:upload:system");
        redisTemplate.delete("rate:upload:system:ts");
    }

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        redisTemplate.delete("rate:upload:system");
        redisTemplate.delete("rate:upload:system:ts");
    }

    private String getAuthCookie() {
        Map<String, String> loginReq = Map.of("username", "admin", "password", "admin");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/auth/login", loginReq, Map.class);
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        return cookies != null && !cookies.isEmpty() ? cookies.get(0) : "";
    }

    @Test
    void testUploadFile_RealIntegration() {
        // Prepare multipart request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        String authCookie = getAuthCookie();
        headers.add(HttpHeaders.COOKIE, authCookie);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        
        // Use a unique file content to avoid SHA-256 conflict on reruns
        String fileContent = "entity_id,order_id,amount,settlement_utr\n" + System.currentTimeMillis() + ",order_1,100,utr_1";
        ByteArrayResource fileAsResource = new ByteArrayResource(fileContent.getBytes()) {
            @Override
            public String getFilename() {
                return "test-gateway-file-" + System.currentTimeMillis() + ".csv";
            }
        };

        body.add("file", fileAsResource);
        body.add("sourceType", "GATEWAY");
        body.add("gateway", "RAZORPAY");
        body.add("settlementDate", "2026-09-28");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/upload", requestEntity, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat((String) response.getBody().get("message")).contains("Chain B pending");
        assertThat(response.getBody().get("status")).isEqualTo("QUEUED");

        // Verify Database insertion
        String fileIdStr = (String) response.getBody().get("fileId");
        assertThat(fileIdStr).isNotNull();
        
        uploadedFileId = UUID.fromString(fileIdStr);
        UploadedFile dbRecord = uploadedFileRepository.findById(uploadedFileId).orElse(null);
        
        assertThat(dbRecord).isNotNull();
        assertThat(dbRecord.getGateway()).isEqualTo("RAZORPAY");
        assertThat(dbRecord.getSourceType()).isEqualTo("GATEWAY");
        assertThat(dbRecord.getStatus()).isEqualTo("QUEUED");
        assertThat(dbRecord.getS3Key()).contains("uploads/");
    }
}
