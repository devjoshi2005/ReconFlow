package com.reconflow.controller;

import com.reconflow.dto.InfrastructureSummaryDTO;
import com.reconflow.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    @GetMapping("/infrastructure")
    public ResponseEntity<InfrastructureSummaryDTO> getInfrastructureSummary() {
        return ResponseEntity.ok(systemService.getInfrastructureSummary());
    }
}
