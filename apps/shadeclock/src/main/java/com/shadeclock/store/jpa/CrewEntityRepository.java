package com.shadeclock.store.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@link CrewEntity} rows.
 *
 * <p>No implementation is written: Spring Data generates one at startup from the method names,
 * so a name that does not match a field is a context-load failure rather than a compile error.
 *
 * <p><b>Every method here names the owner, and that is the security fix expressed as a query.</b>
 * v0.1 read by id alone and listed everything, so holding an id - or simply asking - was the
 * same as being entitled to the data. Pushing the owner into the {@code WHERE} clause means an
 * unauthorised read returns nothing to distinguish from a crew that does not exist: no branch to
 * time, no error message to read. A check that lives in the query cannot be forgotten by a
 * future handler the way an {@code if} in a controller can.
 *
 * <p>Note there is no plain {@code findAll()} exposed upward. {@link JpaRepository} still
 * inherits one, which is why the application depends on {@code CrewRepository} instead - that
 * interface does not offer it, so no controller can reach it.
 */
public interface CrewEntityRepository extends JpaRepository<CrewEntity, String> {

    /**
     * One crew, but only if it belongs to this account.
     *
     * @param id             the crew id
     * @param ownerAccountId the account that must own it
     * @return the row, or empty if it does not exist <em>or</em> is not theirs
     */
    Optional<CrewEntity> findByIdAndOwnerAccountId(String id, String ownerAccountId);

    /**
     * Every crew belonging to one account.
     *
     * @param ownerAccountId the owner
     * @return that account's rows
     */
    List<CrewEntity> findByOwnerAccountId(String ownerAccountId);

    /**
     * Deletes a crew, but only if it belongs to this account.
     *
     * @param id             the crew id
     * @param ownerAccountId the account that must own it
     * @return how many rows were removed: 1 on success, 0 if unknown or not theirs
     */
    long deleteByIdAndOwnerAccountId(String id, String ownerAccountId);
}
