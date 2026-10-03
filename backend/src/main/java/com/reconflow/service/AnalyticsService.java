package com.reconflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconflow.dto.AnalyticsSummaryDTO;
import com.reconflow.repository.ExceptionQueueRepository;
import com.reconflow.repository.LedgerTransactionRepository;
import com.reconflow.repository.UploadedFileRepository;
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
    private final UploadedFileRepository uploadedFileRepo;
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
            // Store with 2s TTL for near real-time updates while avoiding rapid DB hammering
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result), Duration.ofSeconds(2));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize analytics summary for caching", e);
        }
        
        return result;
    }

    private AnalyticsSummaryDTO computeFromPostgres(String range) {
        java.time.ZonedDateTime since = switch (range != null ? range : "90d") {
            case "7d" -> java.time.ZonedDateTime.now().minusDays(7);
            case "30d" -> java.time.ZonedDateTime.now().minusDays(30);
            default -> java.time.ZonedDateTime.now().minusDays(90);
        };

        long totalProcessed = ledgerRepo.countSince(since);
        long totalMatched = ledgerRepo.countMatchedSince(since);
        long openExceptions = exceptionRepo.countOpenExceptions();
        
        long filesQueued = uploadedFileRepo.countByStatusIn(java.util.List.of("PENDING", "PROCESSING"));
        java.util.List<com.reconflow.entity.ExceptionQueue> recentExceptions = exceptionRepo.findTop5ByOrderByCreatedAtDesc();
        
        double overallRate = totalProcessed > 0 ? ((double) totalMatched / totalProcessed) * 100 : 0.0;
        
        java.util.List<java.util.Map<String, Object>> totalsByGw = ledgerRepo.countTotalByGatewaySince(since);
        java.util.List<java.util.Map<String, Object>> matchedByGw = ledgerRepo.countMatchedByGatewaySince(since);

        double razorpayRate = calculateRate(totalsByGw, matchedByGw, "RAZORPAY");
        double phonepeRate = calculateRate(totalsByGw, matchedByGw, "PHONEPE");
        double upayRate = calculateRate(totalsByGw, matchedByGw, "UPAY");

        Double avgDays = exceptionRepo.avgResolutionTimeDays();
        String topGateway = exceptionRepo.topExceptionGateway();

        return AnalyticsSummaryDTO.builder()
                .totalProcessed(totalProcessed)
                .successRateOverall(Math.round(overallRate * 100.0) / 100.0)
                .successRateRazorpay(Math.round(razorpayRate * 100.0) / 100.0)
                .successRatePhonePe(Math.round(phonepeRate * 100.0) / 100.0)
                .successRateUpay(Math.round(upayRate * 100.0) / 100.0)
                .openExceptions(openExceptions)
                .avgResolutionTimeDays(avgDays != null ? Math.round(avgDays * 100.0) / 100.0 : 0.0)
                .highestExceptionSource(topGateway != null ? topGateway : "N/A")
                .exceptionsByType(exceptionRepo.countByExceptionType())
                .volumeTimeSeries(ledgerRepo.getVolumeTimeSeries(since))
                .exceptionTimeSeries(exceptionRepo.getExceptionTimeSeries(since))
                .filesQueued(filesQueued)
                .recentExceptions(recentExceptions)
                .build();
    }

    private double calculateRate(java.util.List<java.util.Map<String, Object>> totals, java.util.List<java.util.Map<String, Object>> matched, String gateway) {
        long t = 0;
        long m = 0;
        for (java.util.Map<String, Object> map : totals) {
            if (gateway.equalsIgnoreCase((String) map.get("gateway"))) {
                t = ((Number) map.get("count")).longValue();
            }
        }
        for (java.util.Map<String, Object> map : matched) {
            if (gateway.equalsIgnoreCase((String) map.get("gateway"))) {
                m = ((Number) map.get("count")).longValue();
            }
        }
        return t > 0 ? ((double) m / t) * 100 : 0.0;
    }
}
