package com.caseware.interview.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
}
