package com.refillradar.shortage;

import java.util.List;

import com.refillradar.domain.ShortageRecord;

/**
 * Supplies the current set of FDA drug-shortage records.
 *
 * <p>Two implementations: {@link OpenFdaShortageSource} calls the live API,
 * {@link FixtureShortageSource} replays recorded JSON (the default).
 *
 * <p>The immediate reason for the seam is that this build environment blocks
 * {@code api.fda.gov}. The lasting one: a suite calling a third-party API is slow,
 * rate-limited and red when someone else has an outage - and a CI signal nobody trusts is
 * worse than none. It is also the only way to exercise a malformed record, an unknown
 * status or an empty feed on demand.
 */
public interface ShortageSource {

    /**
     * Fetches the current shortage records.
     *
     * @return the records, never {@code null}; empty if the source has nothing to report
     * @throws ShortageFetchException if the records could not be retrieved or parsed
     */
    List<ShortageRecord> fetchCurrentShortages();

    /**
     * A short label identifying where this data came from.
     *
     * <p>Shown to users alongside every alert. A tool that tells someone their epilepsy
     * medication may be unavailable owes them the provenance of that claim, and a build
     * accidentally running on fixtures must be visibly distinguishable from one running on
     * live FDA data.
     *
     * @return a human-readable source description, never {@code null}
     */
    String describeSource();
}
