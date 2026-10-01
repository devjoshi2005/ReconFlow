package com.reconflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconflow.dto.AnalyticsSummaryDTO;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ExceptionQueueRepository exceptionRepo;
    private final LedgerTransactionRepository ledgerRepo;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AnalyticsSummaryDTO getSummary(String range) {
        String cacheKey = "analytics:summary:" + range;
        
        try {
            // Try cache first (TTL 60s)
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached, AnalyticsSummaryDTO.class);
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed, falling back to DB", e);
        }
        
        // Miss -> hit Postgres
        AnalyticsSummaryDTO result = computeFromPostgres(range);
        
        try {
            // Store with 60s TTL
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofSeconds(60));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize analytics summary for caching", e);
        }
        
        return result;
    }

    private AnalyticsSummaryDTO computeFromPostgres(String range) {
        long totalProcessed = ledgerRepo.count();
        long totalMatched = ledgerRepo.countMatched();
        long openExceptions = exceptionRepo.countOpenExceptions();
        
        double overallRate = totalProcessed > 0 ? ((double) totalMatched / totalProcessed) * 100 : 0.0;
        
        // For demonstration, mocking the specific gateway logic
        // In reality, this requires counting by gateway.
        double razorpayRate = overallRate > 0 ? overallRate - 0.5 : 0.0; 
        double phonepeRate = overallRate > 0 ? overallRate - 1.2 : 0.0;

        return AnalyticsSummaryDTO.builder()
                .totalProcessed(totalProcessed)
                .successRateOverall(Math.round(overallRate * 100.0) / 100.0)
                .successRateRazorpay(Math.round(razorpayRate * 100.0) / 100.0)
                .successRatePhonePe(Math.round(phonepeRate * 100.0) / 100.0)
                .openExceptions(openExceptions)
                .avgResolutionTimeDays(1.2) // Mocked complex calc
                .highestExceptionSource("PHONEPE") // Mocked group by
                .exceptionsByType(exceptionRepo.countByExceptionType())
                .build();
    }
}
