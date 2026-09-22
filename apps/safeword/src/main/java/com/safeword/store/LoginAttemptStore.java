package com.safeword.store;

import java.time.Instant;
import java.util.Optional;

/**
 * Remembers failed login attempts so they can be counted within a time window.
 *
 * <p>Split from {@code LoginThrottle} for the same reason {@code AlertRecordStore} is split
 * from {@code AlertLedger}: the <em>policy</em> - how many failures, over how long, what
 * clears them - is where the interesting bugs live, and it should be testable without a
 * schema. This interface is deliberately dumb: it counts rows under a key.
 */
public interface LoginAttemptStore {

    /**
     * Records one attempt.
     *
     * @param attemptKey what the attempt counts against
     * @param when       when it happened, from the application clock
     */
    void record(String attemptKey, Instant when);

    /**
     * Counts attempts under a key since an instant, exclusive.
     *
     * @param attemptKey what to count
     * @param since      the start of the window; attempts at exactly this instant are out
     * @return how many attempts fall inside the window
     */
    int countSince(String attemptKey, Instant since);

    /**
     * Finds the oldest attempt still inside the window.
     *
     * <p>This is what makes {@code Retry-After} a real number rather than a guess: the
     * block lifts when this attempt ages out, so the client can be told exactly when to
     * come back instead of polling.
     *
     * @param attemptKey what to look under
     * @param since      the start of the window
     * @return the oldest attempt in the window, or empty if there is none
     */
    Optional<Instant> earliestSince(String attemptKey, Instant since);

    /**
     * Forgets every attempt under a key.
     *
     * @param attemptKey what to forget
     */
    void clear(String attemptKey);

    /**
     * Deletes attempts too old to affect any decision.
     *
     * @param cutoff attempts strictly older than this are removed
     * @return how many rows were deleted
     */
    int purgeOlderThan(Instant cutoff);
}
