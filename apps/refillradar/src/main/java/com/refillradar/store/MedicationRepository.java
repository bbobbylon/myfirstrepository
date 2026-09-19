package com.refillradar.store;

import java.util.List;

import com.refillradar.domain.Medication;

/**
 * Stores the medications users have registered.
 *
 * <h2>Why an interface for something this small</h2>
 * v0.1 stores medications in memory so the application runs with no database at all - clone,
 * {@code mvnw spring-boot:run}, done. That is the right trade for proving the engine works,
 * and the wrong one for anything real: a restart loses every user's list.
 *
 * <p>Putting persistence behind this interface means moving to PostgreSQL in v0.2 is a new
 * implementation plus a configuration change, with no edits to the matcher, the alert
 * composer or their tests. The alternative - {@code ConcurrentHashMap} calls scattered
 * through the service layer - turns that migration into a rewrite.
 *
 * <p>The analogy: this is the difference between a building with a <em>plumbing plan</em>
 * and one where each room was connected to the mains by whoever was nearest. Both deliver
 * water; only one can be renovated.
 */
public interface MedicationRepository {

    /**
     * Saves a medication, replacing any existing entry with the same id.
     *
     * @param medication the medication to store; must not be {@code null}
     * @return the stored medication
     */
    Medication save(Medication medication);

    /**
     * Finds every medication belonging to one user.
     *
     * @param userId the owner's id
     * @return that user's medications, never {@code null}; empty if they have none
     */
    List<Medication> findByUserId(String userId);

    /**
     * Returns every stored medication across all users.
     *
     * <p>Used by the nightly sync, which checks the whole population against one fetch of
     * the FDA feed rather than fetching per user.
     *
     * @return all medications, never {@code null}
     */
    List<Medication> findAll();

    /**
     * Deletes a medication by id.
     *
     * @param id the medication id
     * @return {@code true} if something was removed
     */
    boolean deleteById(String id);
}
