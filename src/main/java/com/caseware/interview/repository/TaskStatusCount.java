package com.caseware.interview.repository;

import com.caseware.interview.domain.TaskStatus;

public interface TaskStatusCount {

    TaskStatus getStatus();

    long getTotal();
}
