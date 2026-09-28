package com.caseware.interview.adapter.out.persistence;

import java.util.function.Supplier;

import com.caseware.interview.application.port.out.TransactionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

@Component
@RequiredArgsConstructor
public class SpringTransactionAdapter implements TransactionPort {

    private final TransactionOperations transactions;

    @Override
    public <T> T required(Supplier<T> work) {
        return transactions.execute(ignored -> work.get());
    }
}
