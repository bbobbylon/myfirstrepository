package com.renewalguard.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.renewalguard.domain.BenefitCase;

/**
 * In-memory {@link BenefitCaseRepository} for v0.1.
 *
 * <p><b>Data is lost on restart.</b> Fine for proving the engine, not for someone relying on
 * it for a deadline. PostgreSQL is the v0.2 task.
 *
 * <p>{@link ConcurrentHashMap} because a web application is multi-threaded by definition and
 * a plain {@code HashMap} under concurrent writes can corrupt its own structure.
 */
@Repository
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
    public Optional<BenefitCase> findById(String id) {
        return Optional.ofNullable(storage.get(id));
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
    public boolean deleteById(String id) {
        return storage.remove(id) != null;
    }
}
