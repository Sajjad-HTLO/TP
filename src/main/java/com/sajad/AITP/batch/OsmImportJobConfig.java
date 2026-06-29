package com.sajad.AITP.batch;

import com.sajad.AITP.model.OsmPoi;
import com.sajad.AITP.model.PoiEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class OsmImportJobConfig {

    private final JobRepository            jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final OsmPoiItemReader         reader;
    private final OsmPoiItemProcessor      processor;
    private final PoiJdbcItemWriter        writer;

    @Value("${osm.import.chunk-size:500}")
    private int chunkSize;

    @Bean
    public Job osmImportJob(Step osmImportStep) {
        return new JobBuilder("osmImportJob", jobRepository)
            .start(osmImportStep)
            .build();
    }

    @Bean
    public Step osmImportStep() {
        return new StepBuilder("osmImportStep", jobRepository)
            .<OsmPoi, PoiEntity>chunk(chunkSize)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .transactionManager(transactionManager)
            .build();
    }
}