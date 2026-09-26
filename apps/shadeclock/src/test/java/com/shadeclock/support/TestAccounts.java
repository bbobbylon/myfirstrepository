package com.shadeclock.support;

import java.time.Instant;
import java.util.UUID;

import com.commonauth.domain.Account;
import com.commonauth.domain.AccountRole;

/**
 * Builds accounts for tests.
 *
 * <p>Exists because {@code crews.owner_account_id} is a foreign key, so a test that wants to
 * store a crew must first have an account for it to belong to. That is the constraint working:
 * in v0.1 a crew belonged to nobody, which is exactly why anybody could list one.
 */
public final class TestAccounts {

    /** A valid BCrypt hash, so {@link Account}'s validation passes. Not a real password. */
    public static final String DUMMY_HASH =
            "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

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
}
