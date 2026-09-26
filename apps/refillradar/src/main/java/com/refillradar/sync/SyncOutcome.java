package com.refillradar.sync;

import java.time.Instant;

/**
 * What happened on one run of the shortage sync.
 *
 * @param succeeded        whether the fetch worked
 * @param recordsFetched   how many records came back, or 0 on failure
 * @param usersEvaluated   how many users were checked
 * @param alertsSent       how many alerts were dispatched
 * @param alertsSuppressed how many were suppressed as already-sent duplicates
 * @param ranAt            when the sync ran
 * @param note             plain-language summary, including the failure reason if any
 */
public record SyncOutcome(
        boolean succeeded,
        int recordsFetched,
        int usersEvaluated,
        int alertsSent,
        int alertsSuppressed,
        Instant ranAt,
        String note) {
}
