package com.caseware.interview.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledExecutorService;

import com.caseware.interview.application.port.in.EngagementFileUseCase;
import com.caseware.interview.application.port.in.FanOutUseCase;
import com.caseware.interview.application.port.in.TaskDispatchUseCase;
import com.caseware.interview.application.port.in.TemplatePublicationUseCase;
import com.caseware.interview.application.port.out.EngagementFileCatalogPort;
import com.caseware.interview.application.port.out.EngagementUpdatePort;
import com.caseware.interview.application.port.out.FanOutTaskStorePort;
import com.caseware.interview.application.port.out.PublicationStorePort;
import com.caseware.interview.application.port.out.TransactionPort;
import com.caseware.interview.application.service.DownstreamTaskDispatchService;
import com.caseware.interview.application.service.EngagementFileApplicationService;
import com.caseware.interview.application.service.PublicationApplicationService;
import com.caseware.interview.application.service.TemplatePublishFanOutService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService downstreamExecutor(WorkerProperties properties) {
        return Executors.newFixedThreadPool(
                properties.maxConcurrency(),
                Thread.ofPlatform().name("downstream-update-", 0).factory());
    }

    @Bean
    Semaphore downstreamCapacity(WorkerProperties properties) {
        return new Semaphore(properties.maxConcurrency());
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService leaseRenewalExecutor() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("lease-renewal-", 0).factory());
    }

    @Bean
    EngagementFileUseCase engagementFileUseCase(
            EngagementFileCatalogPort catalog,
            Clock clock) {
        return new EngagementFileApplicationService(catalog, clock);
    }

    @Bean
    TemplatePublicationUseCase templatePublicationUseCase(
            PublicationStorePort publications,
            FanOutTaskStorePort tasks,
            Clock clock) {
        return new PublicationApplicationService(publications, tasks, clock);
    }

    @Bean
    FanOutUseCase fanOutUseCase(
            PublicationStorePort publications,
            EngagementFileCatalogPort files,
            FanOutTaskStorePort tasks,
            WorkerProperties properties,
            Clock clock,
            TransactionPort transactions,
            MeterRegistry metrics) {
        return new TemplatePublishFanOutService(
                publications, files, tasks, properties, clock, transactions, metrics);
    }

    @Bean
    TaskDispatchUseCase taskDispatchUseCase(
            FanOutTaskStorePort tasks,
            EngagementUpdatePort client,
            WorkerProperties properties,
            Clock clock,
            TransactionPort transactions,
            ExecutorService downstreamExecutor,
            ScheduledExecutorService leaseRenewalExecutor,
            Semaphore downstreamCapacity,
            MeterRegistry metrics) {
        return new DownstreamTaskDispatchService(
                tasks, client, properties, clock, transactions,
                downstreamExecutor, leaseRenewalExecutor, downstreamCapacity, metrics);
    }
}
