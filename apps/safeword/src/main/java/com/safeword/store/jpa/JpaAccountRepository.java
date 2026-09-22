package com.safeword.store.jpa;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.safeword.domain.Account;
import com.safeword.store.AccountRepository;

/**
 * PostgreSQL-backed {@link AccountRepository}.
 */
@Repository
@Profile("!memory")
public class JpaAccountRepository implements AccountRepository {

    private final AccountEntityRepository rows;

    /**
     * @param rows Spring Data access to the users table
     */
    public JpaAccountRepository(AccountEntityRepository rows) {
        this.rows = rows;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Account save(Account account) {
        rows.save(AccountEntity.fromDomain(account));
        return account;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findByUsername(String username) {
        return rows.findByUsernameIgnoreCase(username).map(AccountEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findById(String id) {
        return rows.findById(id).map(AccountEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean usernameExists(String username) {
        return rows.existsByUsernameIgnoreCase(username);
    }
}
