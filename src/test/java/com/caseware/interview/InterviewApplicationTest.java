package com.caseware.interview;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class InterviewApplicationTest {

    @Test
    void delegatesStartupToSpringBoot() {
        String[] arguments = {"--spring.main.web-application-type=none"};

        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            InterviewApplication.main(arguments);

            spring.verify(() -> SpringApplication.run(InterviewApplication.class, arguments));
        }
    }
}
