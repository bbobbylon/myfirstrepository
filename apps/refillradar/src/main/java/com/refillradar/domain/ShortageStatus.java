package com.refillradar.domain;

import java.util.Locale;

/**
 * The lifecycle state of a drug shortage as reported by the FDA.
 *
 * <p>openFDA exposes {@code status} as free text. {@link #fromFdaStatus(String)} maps it
 * onto this closed set, so a spelling change at the FDA cannot silently alter behaviour.
 *
 * <p><b>{@link #UNKNOWN} counts as active.</b> An unrecognised status treated as resolved
 * sends no alert, and a wrong guess there means someone reaches the pharmacy believing all
 * is well - false confidence the app itself created. Treated as active, a wrong guess costs
 * one unnecessary question to a pharmacist. The error costs are wildly asymmetric, so
 * {@link #isPotentiallyActive()} fails loudly rather than silently.
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
