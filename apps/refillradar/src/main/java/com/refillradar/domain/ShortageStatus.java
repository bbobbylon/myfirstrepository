package com.refillradar.domain;

import java.util.Locale;

/**
 * The lifecycle state of a drug shortage as reported by the FDA.
 *
 * <p>The openFDA {@code /drug/shortages.json} endpoint exposes a free-text {@code status}
 * field. We deliberately do <em>not</em> bind that string directly into our logic, because
 * a spelling change at the FDA would then silently alter this application's behaviour.
 * Instead {@link #fromFdaStatus(String)} maps the incoming text onto this closed set.
 *
 * <h2>Why {@link #UNKNOWN} counts as active</h2>
 * The single most important design decision in this class is what happens when the FDA
 * sends us a status string we do not recognise. There are two options:
 *
 * <ul>
 *   <li><b>Treat it as resolved.</b> The user gets no alert. If we were wrong, they walk
 *       into the pharmacy believing everything is fine and discover it is not. The app has
 *       actively made them worse off than having no app, because it created false
 *       confidence.</li>
 *   <li><b>Treat it as potentially active.</b> The user gets an alert flagged as
 *       uncertain. If we were wrong, they ask their pharmacist an unnecessary question.</li>
 * </ul>
 *
 * <p>We choose the second. In a safety-adjacent tool the costs of the two error types are
 * wildly asymmetric, so we fail loudly rather than silently. {@link #isPotentiallyActive()}
 * encodes that choice in one place.
 */
public enum ShortageStatus {

    /** The FDA currently lists this product as in shortage. */
    CURRENT,

    /** The shortage has been resolved; supply is reported to have recovered. */
    RESOLVED,

    /** The product has been discontinued outright - a permanent supply problem. */
    DISCONTINUED,

    /**
     * The FDA sent a status string this application does not recognise.
     *
     * <p>Treated as potentially active on purpose - see the class-level note. Encountering
     * this in production is a signal to update the mapping, not to ignore it.
     */
    UNKNOWN;

    /**
     * Maps a raw {@code status} string from the openFDA feed onto this enum.
     *
     * <p>Matching is deliberately loose (lower-cased, substring-based) because the exact
     * casing and wording of the FDA's values are outside our control. Anything unmatched
     * becomes {@link #UNKNOWN} rather than throwing, so one unexpected record cannot abort
     * an entire nightly sync.
     *
     * @param rawStatus the {@code status} field from the API; may be {@code null} or blank
     * @return the mapped status, never {@code null}
     */
    public static ShortageStatus fromFdaStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return UNKNOWN;
        }
        String normalised = rawStatus.toLowerCase(Locale.ROOT).trim();

        // Order matters: check "discontinued" before "current", since a value such as
        // "Discontinued - no longer current" contains both words.
        if (normalised.contains("discontinu")) {
            return DISCONTINUED;
        }
        if (normalised.contains("resolved") || normalised.contains("no longer in shortage")) {
            return RESOLVED;
        }
        if (normalised.contains("current") || normalised.contains("active")
                || normalised.contains("shortage")) {
            return CURRENT;
        }
        return UNKNOWN;
    }

    /**
     * Whether a patient should be warned about a record in this state.
     *
     * @return {@code true} for {@link #CURRENT}, {@link #DISCONTINUED} and {@link #UNKNOWN};
     *         {@code false} only for {@link #RESOLVED}, the one state where we are
     *         affirmatively told supply is fine
     */
    public boolean isPotentiallyActive() {
        return this != RESOLVED;
    }

    /**
     * Whether this status was confidently understood.
     *
     * <p>Used to add a visible "we could not interpret the FDA's status for this record"
     * caveat to user-facing alerts, so uncertainty is shown rather than hidden.
     *
     * @return {@code true} unless this is {@link #UNKNOWN}
     */
    public boolean isConfident() {
        return this != UNKNOWN;
    }
}
