package com.reconflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import com.reconflow.entity.ExceptionQueue;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDTO {
    private long totalProcessed;
    private double successRateOverall;
    private double successRateRazorpay;
    private double successRatePhonePe;
    private double successRateUpay;
    private long openExceptions;
    private double avgResolutionTimeDays;
    private String highestExceptionSource;
    private List<Map<String, Object>> exceptionsByType;
    private List<Map<String, Object>> volumeTimeSeries;
    private List<Map<String, Object>> exceptionTimeSeries;
    private long filesQueued;
    private List<ExceptionQueue> recentExceptions;
}
