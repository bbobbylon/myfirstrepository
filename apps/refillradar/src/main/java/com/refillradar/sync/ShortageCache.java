package com.refillradar.sync;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.refillradar.domain.ShortageRecord;

/**
 * Holds the last shortage feed we successfully fetched, and how old it is.
 *
 * <p>A safety feature, not a performance one. A failed sync does <b>not</b> clear what we
 * already knew: shortages persist for months, so yesterday's feed clearly labelled as
 * yesterday's is far better than silence, whereas an empty result reads as an all-clear.
 * Staleness is surfaced rather than hidden - "we last heard from the FDA six days ago" is
 * something a person relying on this deserves to know.
 *
 * <p>Thread-safe via {@link AtomicReference}: the scheduled sync writes while HTTP requests
 * read, and a torn read would mean serving half a feed.
 */
@Component
public class ShortageCache {

    /** Beyond this age, the cached feed is flagged as stale in user-facing output. */
    public static final Duration STALE_AFTER = Duration.ofHours(36);

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();
    private final Clock clock;

    /**
     * @param clock injected so staleness is testable without waiting a day and a half
     */
    public ShortageCache(Clock clock) {
        this.clock = clock;
    }

    /**
     * Replaces the cached feed after a successful fetch.
     *
     * @param records the freshly fetched records
     * @param sourceLabel provenance of this fetch
     */
    public void store(List<ShortageRecord> records, String sourceLabel) {
        snapshot.set(new Snapshot(List.copyOf(records), sourceLabel, clock.instant()));
    }

    /**
     * The cached feed, if we have ever successfully fetched one.
     *
     * @return the snapshot, or empty if no fetch has ever succeeded
     */
    public Optional<Snapshot> current() {
        return Optional.ofNullable(snapshot.get());
    }

    /**
     * Whether the cache holds data old enough to warn about.
     *
     * @return {@code true} if there is no data at all, or it is older than {@link #STALE_AFTER}
     */
    public boolean isStale() {
        Snapshot held = snapshot.get();
        if (held == null) {
            return true;
        }
        return Duration.between(held.fetchedAt(), clock.instant()).compareTo(STALE_AFTER) > 0;
    }

    /**
     * A plain-language note about the cache's age, for user-facing output.
     *
     * @return the note, never {@code null}
     */
    public String freshnessNote() {
        Snapshot held = snapshot.get();
        if (held == null) {
            // Said outright. A user must never read "no matches" as "you are fine" when the
            // real situation is that we have never managed to fetch anything.
            return "We have not successfully fetched shortage data yet. Do NOT read an empty "
                    + "result as an all-clear.";
        }
        long hours = Duration.between(held.fetchedAt(), clock.instant()).toHours();
        if (isStale()) {
            return "Shortage data is " + hours + " hours old - our last successful update "
                    + "failed or has not run. Please check with your pharmacist.";
        }
        return "Shortage data last updated " + hours + " hour" + (hours == 1 ? "" : "s")
                + " ago.";
    }

    /**
     * One successfully fetched feed.
     *
     * @param records     the records
     * @param sourceLabel where they came from
     * @param fetchedAt   when the fetch succeeded
     */
    public record Snapshot(List<ShortageRecord> records, String sourceLabel, Instant fetchedAt) {
    }
}
