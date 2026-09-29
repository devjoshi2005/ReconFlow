package com.reconflow.service;

import com.reconflow.entity.UploadedFile;
import com.reconflow.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final S3Client s3Client;
    private final SqsClient sqsClient;
    private final UploadedFileRepository uploadedFileRepository;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.sqs.queue-name}")
    private String queueName;

    @Transactional
    public UploadedFile processUpload(MultipartFile file, String sourceType, String gateway, String bankName) throws Exception {
        String checksum = calculateChecksum(file);

        if (uploadedFileRepository.existsByFileChecksum(checksum)) {
            throw new IllegalArgumentException("File with this checksum has already been uploaded.");
        }

        String s3Key = "uploads/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        // 1. Upload to S3
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build(), RequestBody.fromBytes(file.getBytes()));

        // 2. Save DB Record
        UploadedFile uploadedFile = UploadedFile.builder()
                .fileName(file.getOriginalFilename())
                .s3Key(s3Key)
                .fileChecksum(checksum)
                .sourceType(sourceType)
                .gateway(gateway)
                .bankName(bankName)
                .status("QUEUED")
                .build();
        
        uploadedFile = uploadedFileRepository.save(uploadedFile);

        // 3. Push to SQS
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
        String messageBody = String.format("{\"fileId\": \"%s\"}", uploadedFile.getId());
        
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build());

        log.info("Successfully queued file upload: {}", uploadedFile.getId());
        return uploadedFile;
    }

    private String calculateChecksum(MultipartFile file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = file.getInputStream()) {
            byte[] byteArray = new byte[1024];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public java.util.List<UploadedFile> getAllFiles() {
        return uploadedFileRepository.findAll();
    }

    public java.util.Optional<UploadedFile> getFileById(UUID id) {
        return uploadedFileRepository.findById(id);
    }
}
