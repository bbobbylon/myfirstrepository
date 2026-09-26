package com.renewalguard.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.renewalguard.domain.BenefitCase;

/**
 * In-memory {@link BenefitCaseRepository}, kept for the {@code memory} profile.
 *
 * <p><b>Data is lost on restart</b>, so this is no longer the default: since v0.2 PostgreSQL
 * is, and an unreachable database stops the application starting. This implementation
 * survives because unit tests that exercise the projection maths should not need a database
 * to run, and because {@code MemoryProfileTest} proves the application can still boot without
 * one - which is what keeps the storage seam honest rather than decorative.
 *
 * <p>{@link ConcurrentHashMap} because a web application is multi-threaded by definition and
 * a plain {@code HashMap} under concurrent writes can corrupt its own structure.
 */
@Repository
@Profile("memory")
public class InMemoryBenefitCaseRepository implements BenefitCaseRepository {

    private final Map<String, BenefitCase> storage = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public BenefitCase save(BenefitCase benefitCase) {
        storage.put(benefitCase.id(), benefitCase);
        return benefitCase;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<BenefitCase> findByIdAndUserId(String id, String userId) {
        // The owner check is part of the lookup rather than the caller's job, exactly as it
        // is part of the SQL WHERE clause in the JPA implementation. Two implementations of
        // one interface have to agree about who may read what, or a test that passes against
        // the fast one proves nothing about the real one.
        return Optional.ofNullable(storage.get(id))
                .filter(benefitCase -> benefitCase.userId().equals(userId));
    }

    /** {@inheritDoc} */
    @Override
    public List<BenefitCase> findByUserId(String userId) {
        return storage.values().stream()
                .filter(benefitCase -> benefitCase.userId().equals(userId))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<BenefitCase> findAll() {
        return List.copyOf(storage.values());
    }

    /** {@inheritDoc} */
    @Override
    public boolean deleteByIdAndUserId(String id, String userId) {
        BenefitCase existing = storage.get(id);
        if (existing == null || !existing.userId().equals(userId)) {
            return false;
        }
        // The two-argument remove, not remove(id): it deletes only while the stored value is
        // still the one just checked, so two concurrent deletes cannot both report success
        // and a write that slipped in between is not silently discarded. Records compare by
        // value, which is what makes the second argument meaningful.
        return storage.remove(id, existing);
    }
}
