package com.caseware.interview.domain;

public record TaskCounts(long pending, long processing, long retrying, long succeeded, long deadLetter) {

    public long total() {
        return pending + processing + retrying + succeeded + deadLetter;
    }
}
