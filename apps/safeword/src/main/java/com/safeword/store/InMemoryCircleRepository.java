package com.safeword.store;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.safeword.domain.FamilyCircle;

/**
 * In-memory {@link CircleRepository}, for the {@code memory} profile and for unit tests.
 *
 * <p>Keyed by owner rather than by circle id, so the in-memory implementation cannot offer a
 * lookup the interface does not - a test double that is more permissive than the real thing
 * hides exactly the bug it should catch.
 *
 * <p><b>Loses every circle on restart</b>, which is why it is not the default.
 */
@Repository
@Profile("memory")
public class InMemoryCircleRepository implements CircleRepository {

    private final Map<String, FamilyCircle> byOwner = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public FamilyCircle save(String ownerAccountId, FamilyCircle circle) {
        byOwner.put(ownerAccountId, circle);
        return circle;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<FamilyCircle> findByOwner(String ownerAccountId) {
        return Optional.ofNullable(byOwner.get(ownerAccountId));
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsForOwner(String ownerAccountId) {
        return byOwner.containsKey(ownerAccountId);
    }
}
