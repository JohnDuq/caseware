package com.caseware.interview.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEngagementUpdateClient implements EngagementUpdateClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingEngagementUpdateClient.class);

    @Override
    public void evaluatePendingUpdate(String fileId, String targetVersion, String idempotencyKey) {
        log.info("Simulated downstream evaluation: file={}, targetVersion={}, idempotencyKey={}",
                fileId, targetVersion, idempotencyKey);
    }
}
