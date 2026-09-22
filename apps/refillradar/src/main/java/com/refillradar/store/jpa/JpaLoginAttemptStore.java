package com.refillradar.store.jpa;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.refillradar.store.LoginAttemptStore;

/**
 * PostgreSQL-backed {@link LoginAttemptStore}. The default.
 *
 * <p>The throttle counts in the database rather than in the process for two reasons that
 * only show up in production: a restart must not hand an attacker a fresh budget, and every
 * replica behind a load balancer has to share one. v0.3 already made PostgreSQL mandatory,
 * so this costs no new infrastructure.
 */
@Repository
@Profile("!memory")
public class JpaLoginAttemptStore implements LoginAttemptStore {

    private final LoginAttemptEntityRepository rows;

    /**
     * @param rows Spring Data access to the login_attempts table
     */
    public JpaLoginAttemptStore(LoginAttemptEntityRepository rows) {
        this.rows = rows;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void record(String attemptKey, Instant when) {
        rows.save(LoginAttemptEntity.of(attemptKey, when));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public int countSince(String attemptKey, Instant since) {
        return rows.countByAttemptKeyAndAttemptedAtAfter(attemptKey, since);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> earliestSince(String attemptKey, Instant since) {
        return rows.findFirstByAttemptKeyAndAttemptedAtAfterOrderByAttemptedAtAsc(
                        attemptKey, since)
                .map(LoginAttemptEntity::getAttemptedAt);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void clear(String attemptKey) {
        rows.deleteByAttemptKey(attemptKey);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return rows.deleteByAttemptedAtBefore(cutoff);
    }
}
