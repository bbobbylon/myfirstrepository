package com.commonauth.store.jpa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One recorded login or registration attempt.
 *
 * <p>Unlike the other entities here there is no domain record behind it: an attempt has no
 * behaviour and no invariants worth protecting, it is a tally mark with a timestamp.
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttemptEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String attemptKey;

    @Column(nullable = false)
    private Instant attemptedAt;

    /** Required by JPA. Not for application code - use {@link #of}. */
    protected LoginAttemptEntity() {
    }

    /**
     * Builds a row.
     *
     * @param attemptKey  what the attempt counts against
     * @param attemptedAt when it happened
     * @return the entity to persist
     */
    public static LoginAttemptEntity of(String attemptKey, Instant attemptedAt) {
        LoginAttemptEntity entity = new LoginAttemptEntity();
        entity.id = UUID.randomUUID().toString();
        entity.attemptKey = attemptKey;
        entity.attemptedAt = attemptedAt;
        return entity;
    }

    /**
     * @return when the attempt happened
     */
    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
