package com.shadeclock.crew;

/**
 * How heat-adapted a worker currently is.
 *
 * <p>The body adapts to heat over one to two weeks. Someone who has not been through that
 * is at materially higher risk than the colleague beside them doing identical work - so the
 * most dangerous day is often a first day, or a first day back after leave. A foreman
 * juggling twelve people cannot hold that in their head on a 100°F afternoon; software can.
 *
 * <p>⚠️ The day counts below are ShadeClock's own bands, chosen to line up with the
 * commonly-cited one-to-two-week adaptation window. They are <b>not</b> lifted from a
 * specific regulation. Where a jurisdiction sets its own acclimatisation period, that
 * ruleset governs - see {@code com.shadeclock.rules}.
 */
public enum AcclimatizationStatus {

    /** First three days in heat. Highest risk; closest supervision warranted. */
    UNACCLIMATIZED("New to heat exposure - highest risk, needs close observation"),

    /** Days 4-14. Adapting, but not yet fully adapted. */
    ACCLIMATIZING("Still adapting to heat - increased risk remains"),

    /** More than 14 continuous days working in heat. */
    ACCLIMATIZED("Adapted to current heat exposure"),

    /**
     * Returning after an absence long enough to lose adaptation.
     *
     * <p>Treated the same as {@link #UNACCLIMATIZED} for risk purposes, but named
     * separately because the human explanation differs - and a supervisor who sees
     * "returning from leave" understands the warning in a way "unacclimatized" does not,
     * for a worker they know has been on the job for years.
     */
    LOST_ACCLIMATIZATION("Returning after time away - adaptation is lost and must rebuild");

    private final String explanation;

    AcclimatizationStatus(String explanation) {
        this.explanation = explanation;
    }

    /**
     * A plain-language explanation aimed at a supervisor, not a physiologist.
     *
     * @return the explanation, never {@code null}
     */
    public String explanation() {
        return explanation;
    }

    /**
     * Whether a worker in this state needs the extra precautions a ruleset specifies for
     * unacclimatised workers.
     *
     * @return {@code true} for {@link #UNACCLIMATIZED}, {@link #ACCLIMATIZING} and
     *         {@link #LOST_ACCLIMATIZATION}
     */
    public boolean needsExtraPrecautions() {
        return this != ACCLIMATIZED;
    }
}
