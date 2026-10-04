package com.reconflow.batch;

import com.reconflow.dto.ReconciliationResult;
import com.reconflow.entity.LedgerTransaction;
import com.reconflow.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;

@Configuration
@RequiredArgsConstructor
public class ReconciliationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CsvInMemoryLoader csvInMemoryLoader;
    private final ReconciliationProcessor processor;
    private final ReconciliationWriter writer;
    private final LedgerTransactionRepository ledgerRepository;
    private final ChainBTasklet chainBTasklet;

    private final ReconciliationJobListener jobListener;

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public RepositoryItemReader<LedgerTransaction> ledgerItemReader(
            @org.springframework.beans.factory.annotation.Value("#{jobParameters['uploadedFileId']}") String uploadedFileIdStr,
            com.reconflow.repository.UploadedFileRepository uploadedFileRepository) {
        
        com.reconflow.entity.UploadedFile file = uploadedFileRepository.findById(java.util.UUID.fromString(uploadedFileIdStr))
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + uploadedFileIdStr));
                
        return new RepositoryItemReaderBuilder<LedgerTransaction>()
                .name("ledgerItemReader")
                .repository(ledgerRepository)
                .methodName("findByStatusInAndGatewayIgnoreCase")
                .arguments(java.util.Arrays.asList(
                        java.util.Arrays.asList("PENDING", "UNRECONCILED"),
                        file.getGateway()
                ))
                .sorts(Collections.singletonMap("id", Sort.Direction.ASC))
                .pageSize(500)
                .build();
    }

    @Bean
    public Step chainAStep() {
        return new StepBuilder("chainAStep", jobRepository)
                .<LedgerTransaction, ReconciliationResult>chunk(500, transactionManager)
                .reader(ledgerItemReader(null, null))
                .processor(processor)
                .writer(writer)
                .listener(csvInMemoryLoader) // @BeforeStep / @AfterStep hooks
                .build();
    }

    @Bean
    public Step chainBStep() {
        return new StepBuilder("chainBStep", jobRepository)
                .tasklet(chainBTasklet, transactionManager)
                .listener(csvInMemoryLoader) // @BeforeStep hook to load bank CSV
                .build();
    }

    @Bean
    public Job gatewayReconciliationJob() {
        return new JobBuilder("gatewayReconciliationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(chainAStep())
                .build();
    }

    @Bean
    public Job bankReconciliationJob() {
        return new JobBuilder("bankReconciliationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(chainBStep())
                .build();
    }
}
