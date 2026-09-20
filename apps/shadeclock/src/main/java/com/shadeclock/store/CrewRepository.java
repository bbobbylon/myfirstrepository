package com.shadeclock.store;

import java.util.List;
import java.util.Optional;

import com.shadeclock.crew.Crew;

/**
 * Stores the crews a supervisor has set up.
 *
 * <p>An interface for the same reason as RefillRadar's {@code MedicationRepository}: v0.1
 * keeps crews in memory so the app runs with no database at all, and moving to PostgreSQL in
 * v0.2 should be a new implementation rather than a rewrite of the scheduler.
 */
public interface CrewRepository {

    /**
     * Saves a crew, replacing any existing entry with the same id.
     *
     * @param crew the crew to store; must not be {@code null}
     * @return the stored crew
     */
    Crew save(Crew crew);

    /**
     * Finds a crew by id.
     *
     * @param id the crew id
     * @return the crew, or empty if unknown
     */
    Optional<Crew> findById(String id);

    /**
     * Returns every stored crew.
     *
     * @return all crews, never {@code null}
     */
    List<Crew> findAll();

    /**
     * Deletes a crew by id.
     *
     * @param id the crew id
     * @return {@code true} if something was removed
     */
    boolean deleteById(String id);
}
