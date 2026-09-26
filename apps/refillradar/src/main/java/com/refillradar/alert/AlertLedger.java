package com.refillradar.alert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.refillradar.domain.ShortageMatch;
import com.refillradar.domain.SupplyRisk;

/**
 * Remembers what we have already told each user, so a nightly job does not repeat itself.
 *
 * <p>Shortages last months, so a nightly sync with no memory would email the same person
 * nightly for a quarter. They stop reading by week two, and an ignored alert is worse than
 * none because it still implies someone is watching. Same lesson as RenewalGuard's reminder
 * ladder: <b>escalation, not repetition.</b>
 *
 * <p>The subtlety is that de-duplication must not suppress <em>new</em> information. A move
 * from {@link SupplyRisk#MEDIUM} to {@link SupplyRisk#CRITICAL} means days left, not weeks,
 * so {@link #shouldSend} re-alerts on escalation - and again after {@link #REPEAT_AFTER},
 * so a long shortage produces occasional reminders rather than months of silence.
 *
 * <p>This class holds the <em>policy</em> only. Where the record of what was sent actually
 * lives is {@link AlertRecordStore}'s job - since v0.3 a PostgreSQL table, because a ledger
 * that empties on restart re-sends everything it was built to suppress.
 */
@Component
public class AlertLedger {

    /** How long before the same unchanged alert may be sent again. */
    public static final Duration REPEAT_AFTER = Duration.ofDays(14);

    private final AlertRecordStore store;
    private final Clock clock;

    /**
     * @param store where sent alerts are recorded
     * @param clock injected so repeat windows are testable without waiting a fortnight
     */
    public AlertLedger(AlertRecordStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /**
     * Whether this match warrants sending now.
     *
     * @param match the match under consideration
     * @return {@code true} if it is new, has escalated, or the repeat window has elapsed
     */
    public boolean shouldSend(ShortageMatch match) {
        Optional<Entry> found = store.find(keyFor(match));
        if (found.isEmpty()) {
            return true;
        }
        Entry previous = found.get();
        // Escalation is new information, not a repeat. Days left, not weeks.
        if (isMoreUrgent(match.risk(), previous.risk())) {
            return true;
        }
        return Duration.between(previous.sentAt(), clock.instant()).compareTo(REPEAT_AFTER) > 0;
    }

    /**
     * Records that an alert was sent, so the next run knows.
     *
     * @param match the match that was alerted on
     */
    public void record(ShortageMatch match) {
        store.put(keyFor(match), new Entry(match.risk(), clock.instant()));
    }

    /**
     * The last time we alerted on this match, if ever.
     *
     * @param match the match to look up
     * @return the previous entry, or empty
     */
    public Optional<Entry> lastSent(ShortageMatch match) {
        return store.find(keyFor(match));
    }

    /**
     * Clears the ledger. Test support only.
     */
    public void clear() {
        store.clear();
    }

    /**
     * Builds the identity of an alert.
     *
     * <p>Keyed on user, medication and the matched FDA product - not on the match object.
     * The same underlying shortage produces a fresh {@code ShortageMatch} on every sync, so
     * anything object-identity-based would de-duplicate nothing at all.
     *
     * @param match the match
     * @return the ledger key
     */
    private String keyFor(ShortageMatch match) {
        return match.medication().userId() + "|" + match.medication().id() + "|"
                + match.shortage().displayName();
    }

    /**
     * Whether one risk level is more urgent than another.
     *
     * <p>Compares by ordinal, which works because {@link SupplyRisk} is declared
     * most-urgent-first. That is a real coupling between two files, so it is stated here and
     * asserted in the tests rather than left as a quiet assumption.
     *
     * @param candidate the new risk
     * @param previous  the risk we last alerted at
     * @return {@code true} if the candidate is more urgent
     */
    private boolean isMoreUrgent(SupplyRisk candidate, SupplyRisk previous) {
        return candidate.ordinal() < previous.ordinal();
    }

    /**
     * One recorded alert.
     *
     * @param risk   the urgency at the time it was sent
     * @param sentAt when it was sent
     */
    public record Entry(SupplyRisk risk, Instant sentAt) {
    }
}
