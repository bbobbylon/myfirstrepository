package com.refillradar.store;

import java.util.List;

import com.refillradar.domain.Medication;

/**
 * Stores the medications users have registered.
 *
 * <p>v0.1 stores them in memory so the app runs with no database. Putting persistence behind
 * an interface means moving to PostgreSQL is a new implementation plus config, rather than a
 * rewrite of the matcher and its tests.
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
