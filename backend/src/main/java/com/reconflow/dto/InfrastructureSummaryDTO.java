package com.reconflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfrastructureSummaryDTO {
    private Double cpuUtilization;
    private Double memoryUtilization;
    private Integer sqsMainDepth;
    private Integer sqsDlqDepth;
    private Integer s3FileCount;
    private Long s3TotalSize;
    private Double apiLatencyP50;
    private Double apiLatencyP95;
    private Double apiLatencyP99;
}
