package com.caseware.interview.adapter.in.scheduler;

import com.caseware.interview.application.port.in.FanOutUseCase;
import com.caseware.interview.config.WorkerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TemplatePublishScheduler {

    private final FanOutUseCase fanOut;
    private final WorkerProperties properties;

    @Scheduled(fixedDelayString = "${worker.poll-delay:250ms}")
    public void run() {
        if (properties.schedulingEnabled()) {
            fanOut.drainAvailablePages();
        }
    }
}
