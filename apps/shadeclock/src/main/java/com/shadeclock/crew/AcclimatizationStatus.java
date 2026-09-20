package com.shadeclock.crew;

/**
 * How heat-adapted a worker currently is.
 *
 * <h2>Why this exists at all</h2>
 * Acclimatisation is the single most under-managed factor in heat illness. The body adapts
 * to working in heat over roughly one to two weeks - sweating earlier, sweating more, losing
 * less salt. A worker who has not been through that period is at materially higher risk than
 * the colleague beside them doing identical work in identical weather.
 *
 * <p>The practical consequence is that the most dangerous day is often someone's <em>first
 * day</em>, or their first day back after a holiday or illness. A foreman juggling twelve
 * people cannot reliably hold "who started on Tuesday, and who was off sick last week" in
 * their head on a 100°F afternoon. Software remembers it without effort, which is precisely
 * the kind of dull bookkeeping worth automating.
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
