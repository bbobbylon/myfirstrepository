package com.renewalguard.store;

import java.util.List;
import java.util.Optional;

import com.renewalguard.domain.BenefitCase;

/**
 * Stores the benefit cases users are tracking.
 *
 * <p>An interface for the same reason as the other apps: the in-memory implementation runs
 * with no database for fast unit tests, and PostgreSQL arrived as a second implementation
 * rather than a rewrite.
 *
 * <p><b>Note what this interface does not offer: there is no {@code findById(String)}.</b>
 * v0.1 had one, and that single method was the whole vulnerability - a caller who held a case
 * id could read the case, because the only thing the lookup asked for was the id. The v0.2
 * signatures take the owner as well, so "read a case without establishing whose it is" is no
 * longer a sentence you can write in this codebase.
 *
 * <p>That is deliberately stronger than checking ownership in the controller. A controller
 * check is one {@code if} a future handler can forget; a missing method is a compile error.
 * Prevention beats detection, and making the insecure call <em>unexpressible</em> beats both.
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
     * Finds one of a user's own cases.
     *
     * <p>Returns empty both when the case does not exist and when it belongs to somebody
     * else, and the caller must not try to tell those apart. A handler that answered 404 for
     * one and 403 for the other would be an existence oracle: "this case id is real, just not
     * yours" is precisely the fact an attacker enumerating ids is trying to learn.
     *
     * @param id     the case id
     * @param userId the account that must own it
     * @return the case, or empty if unknown or not theirs
     */
    Optional<BenefitCase> findByIdAndUserId(String id, String userId);

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
     * than querying per user. Legitimate precisely because it is a batch job with no caller
     * to impersonate: no HTTP request reaches this method, so there is no session whose owner
     * it could be ignoring.
     *
     * @return all cases, never {@code null}
     */
    List<BenefitCase> findAll();

    /**
     * Deletes one of a user's own cases.
     *
     * @param id     the case id
     * @param userId the account that must own it
     * @return {@code true} if a row was removed; {@code false} if unknown or not theirs
     */
    boolean deleteByIdAndUserId(String id, String userId);
}
