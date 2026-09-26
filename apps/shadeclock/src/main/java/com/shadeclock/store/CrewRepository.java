package com.shadeclock.store;

import java.util.List;
import java.util.Optional;

import com.shadeclock.crew.Crew;

/**
 * Stores the crews a supervisor has set up.
 *
 * <p>An interface for the same reason as the other apps: the in-memory implementation runs with
 * no database for fast unit tests, and PostgreSQL arrived as a second implementation rather than
 * a rewrite of the scheduler.
 *
 * <p><b>Note two methods that v0.1 had and this does not: {@code findAll()} and
 * {@code findById(String)}.</b>
 *
 * <p>{@code findAll()} is the more serious removal. It had exactly one caller -
 * {@code GET /api/crews}, which was open to anyone - so a single unauthenticated request returned
 * every crew in the system: every worker's name, the dates that reveal who has been off for a
 * week or more, and the coordinates of every work site. There was no id to guess. ShadeClock has
 * no batch job, so nothing legitimate needed it, and the method is gone rather than guarded. A
 * method that does not exist cannot be called by a route somebody adds next year without thinking
 * about who is asking.
 *
 * <p>{@code findById} is the ordinary IDOR removal: every lookup now takes the owner too, so
 * "read a crew without establishing whose it is" is not a sentence this codebase can express. A
 * controller check is one {@code if} a future handler can forget; a missing method is a compile
 * error.
 *
 * <p>The owner is passed as a parameter rather than carried on {@link Crew}, following SafeWord:
 * the domain object stays about heat and adaptation, and an account id never appears in a response
 * that did not need it. It also means v0.2 changed no domain record, so all 76 pre-existing unit
 * tests still construct crews exactly as they did.
 */
public interface CrewRepository {

    /**
     * Saves a crew owned by one account, replacing this account's existing crew with the same id.
     *
     * @param ownerAccountId the supervisor's account id, taken from the session
     * @param crew           the crew to store; must not be {@code null}
     * @return the stored crew
     * @throws IllegalStateException if a crew with this id exists and belongs to another account.
     *         Refusing loudly matters: ids are server-generated UUIDs, so this can only mean a bug
     *         or an attack, and a silent overwrite would hand the caller somebody else's roster
     */
    Crew save(String ownerAccountId, Crew crew);

    /**
     * Finds one of an account's own crews.
     *
     * <p>Returns empty both when the crew does not exist and when it belongs to somebody else, and
     * the caller must not try to tell those apart. A handler that answered 404 for one and 403 for
     * the other would be an existence oracle.
     *
     * @param id             the crew id
     * @param ownerAccountId the account that must own it
     * @return the crew, or empty if unknown or not theirs
     */
    Optional<Crew> findByIdAndOwner(String id, String ownerAccountId);

    /**
     * Finds every crew belonging to one account.
     *
     * @param ownerAccountId the owner
     * @return that account's crews, never {@code null}
     */
    List<Crew> findByOwner(String ownerAccountId);

    /**
     * Deletes one of an account's own crews.
     *
     * <p>New in v0.2. This app stores named workers, so a supervisor needs a way to remove a roster
     * once a job ends - an app that can only accumulate people's names is a retention problem, and
     * refusing to hold data you no longer need is the same privacy argument that keeps this schema
     * small.
     *
     * @param id             the crew id
     * @param ownerAccountId the account that must own it
     * @return {@code true} if a row was removed; {@code false} if unknown or not theirs
     */
    boolean deleteByIdAndOwner(String id, String ownerAccountId);
}
