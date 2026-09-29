package com.reconflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class DlqListenerService {

    private final SqsClient sqsClient;

    @Value("${aws.sqs.dlq-name:reconciliation-jobs-dlq}")
    private String dlqName;

    @Scheduled(fixedDelay = 60000) // Poll DLQ every minute
    public void pollDeadLetterQueue() {
        try {
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(dlqName).build()).queueUrl();
            
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(5) // Short polling for DLQ
                    .build();
            
            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();
            
            for (Message message : messages) {
                log.error("ALERT: Received failed message from DLQ: {}", message.body());
                
                // Usually we store this in ExceptionQueue or Alerting system
                // For now we just log it and delete it so we don't process it infinitely
                
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to poll DLQ", e);
        }
    }
}
