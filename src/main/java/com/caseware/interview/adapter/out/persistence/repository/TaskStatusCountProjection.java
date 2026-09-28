package com.caseware.interview.adapter.out.persistence.repository;

import com.caseware.interview.domain.TaskStatus;

public interface TaskStatusCountProjection {

    TaskStatus getStatus();

    long getTotal();
}
