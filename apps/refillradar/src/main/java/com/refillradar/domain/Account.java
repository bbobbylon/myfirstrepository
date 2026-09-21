package com.refillradar.domain;

import java.time.Instant;

/**
 * A registered account.
 *
 * <p>Named {@code Account} rather than {@code User} on purpose: {@code User} is already
 * taken by Spring Security's own class, and two types with the same short name in one
 * codebase produce import mistakes that compile.
 *
 * <p><b>{@code passwordHash} is a hash and the field name says so.</b> A field called
 * {@code password} invites a future change that stores one. It is never serialised - see
 * {@code AccountResponse} for what the API is allowed to return.
 *
 * @param id           stable identifier; this is what {@code medications.user_id} points at
 * @param username     what the person types to log in; unique, case-insensitively
 * @param passwordHash a BCrypt hash, never a password
 * @param role         what this account may do
 * @param createdOn    when it was registered
 */
public record Account(
        String id,
        String username,
        String passwordHash,
        AccountRole role,
        Instant createdOn) {

    /**
     * Validates the fields the security layer depends on.
     *
     * @throws IllegalArgumentException if any required field is missing or blank
     */
    public Account {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        // A blank hash would make every password comparison fail closed rather than open,
        // but it would also mean an unusable account was created silently. Reject it here.
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash is required");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
    }

    /**
     * Whether this account may reach admin-only endpoints.
     *
     * @return {@code true} for {@link AccountRole#ADMIN}
     */
    public boolean isAdmin() {
        return role == AccountRole.ADMIN;
    }
}
