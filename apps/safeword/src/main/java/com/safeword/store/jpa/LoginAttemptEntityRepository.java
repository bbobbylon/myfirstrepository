package com.safeword.store.jpa;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@link LoginAttemptEntity} rows.
 */
public interface LoginAttemptEntityRepository extends JpaRepository<LoginAttemptEntity, String> {

    /**
     * Counts attempts under a key inside a window.
     *
     * <p>A {@code COUNT} in the database rather than loading rows and counting them in Java:
     * the number of rows is chosen by whoever is attacking, and fetching all of them is how
     * a throttle becomes the outage it was meant to prevent.
     *
     * @param attemptKey what to count
     * @param after      the start of the window, exclusive
     * @return how many attempts fall inside it
     */
    int countByAttemptKeyAndAttemptedAtAfter(String attemptKey, Instant after);

    /**
     * Finds the oldest attempt under a key inside a window.
     *
     * @param attemptKey what to look under
     * @param after      the start of the window, exclusive
     * @return the oldest attempt, or empty
     */
    Optional<LoginAttemptEntity> findFirstByAttemptKeyAndAttemptedAtAfterOrderByAttemptedAtAsc(
            String attemptKey, Instant after);

    /**
     * Deletes every attempt under a key.
     *
     * @param attemptKey what to forget
     */
    void deleteByAttemptKey(String attemptKey);

    /**
     * Deletes attempts older than a cutoff.
     *
     * @param cutoff attempts strictly older than this are removed
     * @return how many rows were deleted
     */
    int deleteByAttemptedAtBefore(Instant cutoff);
}
