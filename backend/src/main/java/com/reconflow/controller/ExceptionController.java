package com.reconflow.controller;

import com.reconflow.entity.ExceptionQueue;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.service.JiraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exceptions")
@RequiredArgsConstructor
public class ExceptionController {

    private final ExceptionQueueRepository exceptionRepo;
    private final JiraService jiraService;

    @GetMapping
    public ResponseEntity<List<ExceptionQueue>> getAllExceptions() {
        return ResponseEntity.ok(exceptionRepo.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExceptionQueue> getException(@PathVariable UUID id) {
        return exceptionRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<ExceptionQueue> resolveException(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        return exceptionRepo.findById(id).map(ex -> {
            String action = payload.get("action");
            String notes = payload.get("notes");
            
            ex.setStatus("RESOLVED".equals(action) ? "RESOLVED" : "INVESTIGATING");
            ex.setResolutionNotes(notes);
            ex.setResolutionType(action);
            
            return ResponseEntity.ok(exceptionRepo.save(ex));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/jira")
    public ResponseEntity<ExceptionQueue> createJiraTicket(@PathVariable UUID id) {
        return ResponseEntity.ok(jiraService.raiseJiraTicket(id));
    }
}
