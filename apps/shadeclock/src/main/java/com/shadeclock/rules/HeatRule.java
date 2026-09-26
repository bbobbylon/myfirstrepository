package com.shadeclock.rules;

/**
 * One threshold-triggered requirement within a jurisdiction's heat ruleset.
 *
 * <p><b>Rules as data, not code.</b> {@code if (heatIndex >= 95)} inside a scheduler would be
 * shorter and unreviewable - a safety officer cannot audit an if-statement buried three
 * classes deep. As value objects the ruleset can be printed, diffed, reviewed by someone who
 * does not read Java, and returned over the API so a user sees which rule fired.
 *
 * @param triggerHeatIndexF  the heat index at or above which this requirement applies
 * @param requirement        what the employer must do, in plain language
 * @param restMinutesPerHour mandated rest minutes per working hour, or {@code 0} where the
 *                           rule imposes a measure other than a rest cadence
 * @param citation           where this requirement comes from, so a reader can check it
 */
public record HeatRule(
        double triggerHeatIndexF,
        String requirement,
        int restMinutesPerHour,
        String citation) {

    /**
     * Validates the fields the scheduler relies on.
     *
     * @throws IllegalArgumentException if the rest cadence is negative or longer than an hour
     */
    public HeatRule {
        if (restMinutesPerHour < 0 || restMinutesPerHour > 60) {
            throw new IllegalArgumentException(
                    "restMinutesPerHour must be 0-60, got " + restMinutesPerHour);
        }
    }

    /**
     * Whether this rule is triggered by the given conditions.
     *
     * @param heatIndexF the heat index to test
     * @return {@code true} if the heat index reaches this rule's trigger
     */
    public boolean appliesAt(double heatIndexF) {
        return heatIndexF >= triggerHeatIndexF;
    }

    /**
     * Whether this rule mandates a rest cadence rather than some other measure.
     *
     * @return {@code true} if {@link #restMinutesPerHour} is positive
     */
    public boolean mandatesRest() {
        return restMinutesPerHour > 0;
    }
}
