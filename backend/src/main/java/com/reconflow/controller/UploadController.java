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
            @RequestParam(value = "bankName", required = false) String bankName) {
        
        try {
            UploadedFile uploadedFile = uploadService.processUpload(file, sourceType, gateway, bankName);
            return ResponseEntity.ok(Map.of(
                    "message", "File uploaded and queued successfully",
                    "fileId", uploadedFile.getId(),
                    "status", uploadedFile.getStatus()
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
