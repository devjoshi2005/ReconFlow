package com.reconflow.controller;

import com.reconflow.entity.ExceptionQueue;
import com.reconflow.entity.status.ExceptionStatus;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.security.SecurityUtils;
import com.reconflow.service.JiraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
        String role = SecurityUtils.getCurrentUserRole();
        if ("ROLE_ANALYST".equals(role)) {
            UUID userId = SecurityUtils.getCurrentUserId();
            return ResponseEntity.ok(exceptionRepo.findByAssignedToOrderByCreatedAtDesc(userId));
        }
        return ResponseEntity.ok(exceptionRepo.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExceptionQueue> getException(@PathVariable UUID id) {
        return exceptionRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolveException(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        String action = payload.get("action");
        ExceptionStatus target = targetStatus(action);
        if (target == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported action: " + action));
        }

        boolean approval = "APPROVE".equalsIgnoreCase(action == null ? "" : action.trim());
        String role = SecurityUtils.getCurrentUserRole();
        UUID userId = SecurityUtils.getCurrentUserId();

        return exceptionRepo.findById(id).map(ex -> {
            // Same data scoping as GET: an analyst may only act on their own exceptions.
            if ("ROLE_ANALYST".equals(role)
                    && (ex.getAssignedTo() == null || !ex.getAssignedTo().equals(userId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Exception is not assigned to the current analyst"));
            }
            // Approval is a manager/admin decision, not an analyst action.
            if (approval && !("ROLE_MANAGER".equals(role) || "ROLE_ADMIN".equals(role))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only managers or admins can approve exceptions"));
            }

            try {
                ex.transitionTo(target);
            } catch (IllegalStateException e) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
            }

            if (approval) {
                ex.setApprovedBy(userId);
            }
            ex.setResolutionNotes(payload.get("notes"));
            ex.setResolutionType(action);
            return ResponseEntity.ok(exceptionRepo.save(ex));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/jira")
    public ResponseEntity<ExceptionQueue> createJiraTicket(@PathVariable UUID id) {
        return ResponseEntity.ok(jiraService.raiseJiraTicket(id));
    }

    private static ExceptionStatus targetStatus(String action) {
        if (action == null || action.isBlank()) {
            return ExceptionStatus.INVESTIGATING;
        }
        return switch (action.trim().toUpperCase()) {
            case "INVESTIGATE", "INVESTIGATING" -> ExceptionStatus.INVESTIGATING;
            case "RESOLVE", "RESOLVED", "APPROVE" -> ExceptionStatus.RESOLVED;
            case "DISMISS", "REJECT", "REJECTED" -> ExceptionStatus.REJECTED;
            default -> null;
        };
    }
}
