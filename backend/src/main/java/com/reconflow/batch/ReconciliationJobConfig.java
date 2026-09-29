package com.reconflow.batch;

import com.reconflow.dto.GatewaySettlementRecord;
import com.reconflow.dto.ReconciliationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class ReconciliationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ReconciliationProcessor processor;
    private final ReconciliationWriter writer;
    
    // Injected from GatewayReaderConfig
    private final FlatFileItemReader<GatewaySettlementRecord> razorpayCsvReader;
    private final FlatFileItemReader<GatewaySettlementRecord> phonePeCsvReader;

    @Bean
    public Step razorpayReconciliationStep() {
        return new StepBuilder("razorpayReconciliationStep", jobRepository)
                .<GatewaySettlementRecord, ReconciliationResult>chunk(100, transactionManager)
                .reader(razorpayCsvReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Job razorpayReconciliationJob() {
        return new JobBuilder("razorpayReconciliationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(razorpayReconciliationStep())
                .build();
    }

    @Bean
    public Step phonePeReconciliationStep() {
        return new StepBuilder("phonePeReconciliationStep", jobRepository)
                .<GatewaySettlementRecord, ReconciliationResult>chunk(100, transactionManager)
                .reader(phonePeCsvReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Job phonePeReconciliationJob() {
        return new JobBuilder("phonePeReconciliationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(phonePeReconciliationStep())
                .build();
    }
}
