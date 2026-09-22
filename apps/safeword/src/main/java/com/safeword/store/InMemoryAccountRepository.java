package com.safeword.store;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.safeword.domain.Account;

/**
 * In-memory {@link AccountRepository}, for the {@code memory} profile and for unit tests.
 *
 * <p>Keys on the lowercased username so the case-insensitive lookup behaves the same way as
 * the unique index on {@code lower(username)} does in PostgreSQL. A test double that is
 * subtly more permissive than the real thing hides exactly the bugs it is meant to catch.
 */
@Repository
@Profile("memory")
public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> byId = new ConcurrentHashMap<>();
    private final Map<String, String> idByLowercasedUsername = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public Account save(Account account) {
        byId.put(account.id(), account);
        idByLowercasedUsername.put(key(account.username()), account.id());
        return account;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Account> findByUsername(String username) {
        return Optional.ofNullable(idByLowercasedUsername.get(key(username))).map(byId::get);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Account> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** {@inheritDoc} */
    @Override
    public boolean usernameExists(String username) {
        return idByLowercasedUsername.containsKey(key(username));
    }

    private String key(String username) {
        return username == null ? "" : username.toLowerCase(Locale.ROOT);
    }
}
