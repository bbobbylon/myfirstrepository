package com.renewalguard.store.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@link BenefitCaseEntity} rows.
 *
 * <p>No implementation is written: Spring Data generates one at startup from the method
 * names, so a name that does not match a field is a context-load failure rather than a
 * compile error.
 *
 * <p>This interface is deliberately not the one the rest of the application depends on.
 * {@code JpaBenefitCaseRepository} adapts it to {@code BenefitCaseRepository}, so the
 * projector and the reminder ladder keep talking to our own interface in domain terms.
 */
public interface BenefitCaseEntityRepository extends JpaRepository<BenefitCaseEntity, String> {

    /**
     * Every case belonging to one user.
     *
     * @param userId the owner
     * @return that user's rows
     */
    List<BenefitCaseEntity> findByUserId(String userId);

    /**
     * One case, but only if it belongs to this user.
     *
     * <p><b>This two-column lookup is the security fix, expressed as a query.</b> v0.1 read
     * by id alone, so holding an id was the same as being entitled to it. Pushing the owner
     * into the {@code WHERE} clause means an unauthorised read returns nothing to
     * distinguish from a case that does not exist - there is no branch a caller can time or
     * an error message they can read. A check that lives in the query cannot be forgotten by
     * a future handler the way an {@code if} in a controller can.
     *
     * @param id     the case id
     * @param userId the account that must own it
     * @return the row, or empty if it does not exist <em>or</em> is not theirs
     */
    Optional<BenefitCaseEntity> findByIdAndUserId(String id, String userId);

    /**
     * Deletes a case, but only if it belongs to this user.
     *
     * @param id     the case id
     * @param userId the account that must own it
     * @return how many rows were removed: 1 on success, 0 if unknown or not theirs
     */
    long deleteByIdAndUserId(String id, String userId);
}
