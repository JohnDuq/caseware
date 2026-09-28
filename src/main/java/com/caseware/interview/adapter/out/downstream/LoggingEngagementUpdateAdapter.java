package com.caseware.interview.adapter.out.downstream;

import com.caseware.interview.application.port.out.EngagementUpdatePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEngagementUpdateAdapter implements EngagementUpdatePort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEngagementUpdateAdapter.class);

    @Override
    public void evaluatePendingUpdate(String fileId, String targetVersion, String idempotencyKey) {
        log.info("Simulated downstream evaluation: file={}, targetVersion={}, idempotencyKey={}",
                fileId, targetVersion, idempotencyKey);
    }
}
