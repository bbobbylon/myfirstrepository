package com.refillradar.support;

import java.time.Instant;
import java.util.UUID;

import com.refillradar.domain.Account;
import com.refillradar.domain.AccountRole;

/**
 * Builds accounts for tests.
 *
 * <p>Exists because {@code medications.user_id} became a foreign key in V2, so a test that
 * wants to store a medication must first have an account for it to belong to. That is the
 * constraint working: before V2 a medication could reference a user who had never existed.
 */
public final class TestAccounts {

    /** A valid BCrypt hash, so {@link Account}'s validation passes. Not a real password. */
    public static final String DUMMY_HASH = "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private TestAccounts() {
    }

    /**
     * Builds an ordinary user account with a random id.
     *
     * @param username the username
     * @return the account
     */
    public static Account user(String username) {
        return new Account(UUID.randomUUID().toString(), username, DUMMY_HASH,
                AccountRole.USER, Instant.parse("2026-01-01T00:00:00Z"));
    }

    /**
     * Builds an admin account with a random id.
     *
     * @param username the username
     * @return the account
     */
    public static Account admin(String username) {
        return new Account(UUID.randomUUID().toString(), username, DUMMY_HASH,
                AccountRole.ADMIN, Instant.parse("2026-01-01T00:00:00Z"));
    }

    /**
     * Builds an ordinary user account with a fixed id.
     *
     * @param id       the account id
     * @param username the username
     * @return the account
     */
    public static Account user(String id, String username) {
        return new Account(id, username, DUMMY_HASH, AccountRole.USER,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
