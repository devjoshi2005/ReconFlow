package com.reconflow.service;

import com.reconflow.entity.ExceptionQueue;
import com.reconflow.repository.ExceptionQueueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JiraService {

    private final ExceptionQueueRepository exceptionRepo;
    private final RestTemplate restTemplate;

    @Value("${jira.url}")
    private String jiraUrl;

    @Value("${jira.username}")
    private String jiraUsername;

    @Value("${jira.api-token}")
    private String jiraApiToken;

    @Value("${jira.project-key}")
    private String projectKey;

    public JiraService(ExceptionQueueRepository exceptionRepo) {
        this.exceptionRepo = exceptionRepo;
        this.restTemplate = new RestTemplate();
    }

    public ExceptionQueue raiseJiraTicket(UUID exceptionId) {
        ExceptionQueue ex = exceptionRepo.findById(exceptionId)
                .orElseThrow(() -> new RuntimeException("Exception not found"));

        // Mocking the Jira API Call for now based on user's instruction to test later
        // In a real scenario, we would use restTemplate.exchange(...) with Basic Auth
        String mockTicketId = projectKey + "-" + (int)(Math.random() * 10000);
        String mockTicketUrl = jiraUrl + "/browse/" + mockTicketId;
        
        ex.setJiraTicketId(mockTicketId);
        ex.setJiraTicketUrl(mockTicketUrl);
        ex.setJiraPriority(ex.getDiscrepancyAmount() != null && ex.getDiscrepancyAmount().abs().doubleValue() > 1000 ? "High" : "Medium");
        ex.setJiraCreatedAt(ZonedDateTime.now());
        ex.setJiraStatus("To Do");

        return exceptionRepo.save(ex);
    }
}
