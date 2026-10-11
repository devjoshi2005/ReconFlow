package com.reconflow.service;

import com.reconflow.entity.ExceptionQueue;
import com.reconflow.repository.ExceptionQueueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
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

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(jiraUsername, jiraApiToken);

            // Constructing a basic Jira issue payload
            Map<String, Object> project = new HashMap<>();
            project.put("key", projectKey);

            Map<String, Object> issuetype = new HashMap<>();
            issuetype.put("name", "Task"); // Assuming standard issue type

            Map<String, Object> fields = new HashMap<>();
            fields.put("project", project);
            fields.put("summary", "Reconciliation Exception: " + ex.getMatchType() + " on " + ex.getMatchId());
            fields.put("description", "Discrepancy Amount: " + ex.getDiscrepancyAmount() + " " + ex.getCurrency());
            fields.put("issuetype", issuetype);

            Map<String, Object> payload = new HashMap<>();
            payload.put("fields", fields);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(jiraUrl + "/rest/api/2/issue", request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                String ticketKey = (String) response.getBody().get("key");
                ex.setJiraTicketId(ticketKey);
                ex.setJiraTicketUrl(jiraUrl + "/browse/" + ticketKey);
                ex.setJiraPriority("Medium"); // default or parse from response
                ex.setJiraCreatedAt(ZonedDateTime.now());
                ex.setJiraStatus("To Do");
            }
        } catch (Exception e) {
            log.error("Failed to create Jira ticket", e);
            throw new RuntimeException("Failed to communicate with Jira API", e);
        }

        return exceptionRepo.save(ex);
    }
}
