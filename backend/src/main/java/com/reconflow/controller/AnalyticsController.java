package com.reconflow.controller;

import com.reconflow.dto.AnalyticsSummaryDTO;
import com.reconflow.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDTO> getSummary(@RequestParam(defaultValue = "all") String range) {
        return ResponseEntity.ok(analyticsService.getSummary(range));
    }
}
