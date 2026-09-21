package com.refillradar.store;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * In-memory {@link ContactRepository}, for the {@code memory} profile and for unit tests.
 *
 * <p><b>Data is lost on restart.</b> Only reachable by explicitly activating the
 * {@code memory} profile, which the README documents as a demo mode.
 */
@Repository
@Profile("memory")
public class InMemoryContactRepository implements ContactRepository {

    private final Map<String, String> emailByUserId = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public void setEmail(String userId, String email) {
        emailByUserId.put(userId, email);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<String> findEmail(String userId) {
        return Optional.ofNullable(emailByUserId.get(userId));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReachable(String userId) {
        return emailByUserId.containsKey(userId);
    }
}
