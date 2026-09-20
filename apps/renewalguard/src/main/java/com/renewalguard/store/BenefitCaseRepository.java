package com.renewalguard.store;

import java.util.List;
import java.util.Optional;

import com.renewalguard.domain.BenefitCase;

/**
 * Stores the benefit cases users are tracking.
 *
 * <p>An interface for the same reason as the other apps: v0.1 is in memory so it runs with
 * no database, and moving to PostgreSQL should be a new implementation rather than a rewrite.
 */
public interface BenefitCaseRepository {

    /**
     * Saves a case, replacing any existing entry with the same id.
     *
     * @param benefitCase the case to store
     * @return the stored case
     */
    BenefitCase save(BenefitCase benefitCase);

    /**
     * Finds a case by id.
     *
     * @param id the case id
     * @return the case, or empty if unknown
     */
    Optional<BenefitCase> findById(String id);

    /**
     * Finds every case belonging to one user.
     *
     * @param userId the owner
     * @return that user's cases, never {@code null}
     */
    List<BenefitCase> findByUserId(String userId);

    /**
     * Returns every stored case.
     *
     * <p>Used by the daily reminder sweep, which evaluates the whole population once rather
     * than querying per user.
     *
     * @return all cases, never {@code null}
     */
    List<BenefitCase> findAll();

    /**
     * Deletes a case by id.
     *
     * @param id the case id
     * @return {@code true} if something was removed
     */
    boolean deleteById(String id);
}
