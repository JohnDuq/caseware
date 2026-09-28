package com.caseware.interview.application.port.in;

public interface FanOutUseCase {

    int drainAvailablePages();

    boolean processOnePage();
}
