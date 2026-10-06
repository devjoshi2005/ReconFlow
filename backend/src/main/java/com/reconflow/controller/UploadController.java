package com.reconflow.controller;

import com.reconflow.entity.UploadedFile;
import com.reconflow.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceType") String sourceType,
            @RequestParam(value = "gateway", required = false) String gateway,
            @RequestParam(value = "bankName", required = false) String bankName,
            @RequestParam("settlementDate") String settlementDateStr,
            @RequestParam(value = "minAmount", defaultValue = "0.01") String minAmount,
            @RequestParam(value = "maxAmount", defaultValue = "10000000") String maxAmount) {
        
        try {
            java.time.LocalDate settlementDate = java.time.LocalDate.parse(settlementDateStr);
            if ("LEDGER".equalsIgnoreCase(sourceType)) {
                UploadedFile uploadedFile = uploadService.processLedgerUpload(file, gateway, settlementDate);
                return ResponseEntity.status(201).body(Map.of(
                        "message", "Ledger file uploaded and processed directly to DB.",
                        "fileId", uploadedFile.getId(),
                        "status", uploadedFile.getStatus(),
                        "chainBPending", false
                ));
            }
            UploadedFile uploadedFile = uploadService.processUpload(file, sourceType, gateway, bankName, settlementDate, minAmount, maxAmount);
            
            boolean counterpartExists = uploadService.checkCounterpartExists(sourceType, settlementDate);
            boolean chainBPending = !counterpartExists;
            
            String message = "File uploaded and queued successfully.";
            if (chainBPending) {
                if ("GATEWAY".equalsIgnoreCase(sourceType)) {
                    message = "Chain B pending — upload bank statement for " + settlementDateStr + " to complete reconciliation.";
                } else {
                    message = "Chain B pending — gateway file for " + settlementDateStr + " not yet received.";
                }
            }
            
            return ResponseEntity.ok(Map.of(
                    "message", message,
                    "fileId", uploadedFile.getId(),
                    "status", uploadedFile.getStatus(),
                    "chainBPending", chainBPending
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process upload: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllFiles() {
        return ResponseEntity.ok(uploadService.getAllFiles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getFileStatus(@PathVariable UUID id) {
        return uploadService.getFileById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
