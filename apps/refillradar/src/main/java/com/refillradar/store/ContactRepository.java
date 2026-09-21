package com.refillradar.store;

import java.util.Optional;

/**
 * Maps a user to somewhere an alert can actually reach them.
 *
 * <p>Held separately from {@link MedicationRepository} deliberately. A medication list is
 * sensitive health data; an email address is contact data. Keeping them in different stores
 * means a future encryption or retention policy can treat them differently, and it keeps the
 * medication records free of anything that is not clinically relevant.
 *
 * <p>v0.1 had no concept of contact at all, because checks were on demand - the user was
 * already looking at the screen. The moment alerts are pushed rather than pulled, we need
 * somewhere to push them.
 */
public interface ContactRepository {

    /**
     * Records where a user can be reached.
     *
     * @param userId the user
     * @param email  their email address
     */
    void setEmail(String userId, String email);

    /**
     * Finds a user's email address.
     *
     * @param userId the user
     * @return the address, or empty if we have none
     */
    Optional<String> findEmail(String userId);

    /**
     * Whether we can reach this user at all.
     *
     * @param userId the user
     * @return {@code true} if a contact route exists
     */
    boolean isReachable(String userId);
}
