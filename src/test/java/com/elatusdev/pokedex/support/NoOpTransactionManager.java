package com.elatusdev.pokedex.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

// TEST SCOPE ONLY, and temporary — see InMemoryUserStore. The use cases are the transaction
// boundary and carry @Transactional, which needs a PlatformTransactionManager to exist. JPA
// contributes one; JPA arrives with WU-US03-A, on another branch.
//
// This does not simulate a transaction and is not trying to: the in-memory fakes it runs
// against have no rollback either. Component tests here therefore prove routing, auth and
// token behaviour — NOT atomicity. DELETE with the fakes when WU-US03-A merges.
@Component
public class NoOpTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        // nothing is enlisted, so there is nothing to make durable
    }

    @Override
    public void rollback(TransactionStatus status) {
        // in-memory fakes cannot roll back; a test that needs atomicity needs Postgres
    }
}
