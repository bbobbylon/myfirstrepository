package com.safeword.store;

import java.util.Optional;

import com.safeword.domain.FamilyCircle;

/**
 * Stores family circles, reachable only through the account that owns them.
 *
 * <p><b>There is deliberately no {@code findById}.</b> v0.1 had one, and the controller
 * called it with an id taken straight from the URL - which is how anyone holding a circle id
 * could read that family's setup and raise an alarm to them. Deleting the lookup is a
 * stronger fix than guarding it: a method that does not exist cannot be called by a route
 * somebody adds next year without thinking about ownership.
 */
public interface CircleRepository {

    /**
     * Creates or replaces the circle belonging to an account.
     *
     * <p>Replacing swaps the whole member list, so removing a responder is a save without
     * them rather than a separate delete call nobody would remember to make.
     *
     * @param ownerAccountId the owning account
     * @param circle         the circle to store
     * @return the stored circle
     */
    FamilyCircle save(String ownerAccountId, FamilyCircle circle);

    /**
     * Finds the circle belonging to an account.
     *
     * @param ownerAccountId the owning account
     * @return the circle, or empty if the account has not set one up
     */
    Optional<FamilyCircle> findByOwner(String ownerAccountId);

    /**
     * Whether an account already has a circle.
     *
     * @param ownerAccountId the owning account
     * @return {@code true} if one exists
     */
    boolean existsForOwner(String ownerAccountId);
}
