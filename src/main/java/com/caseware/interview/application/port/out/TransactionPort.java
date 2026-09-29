package com.caseware.interview.application.port.out;

import java.util.function.Supplier;

public interface TransactionPort {

    <T> T required(Supplier<T> work);
}
