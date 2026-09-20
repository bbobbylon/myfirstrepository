package com.shadeclock.rules;

/**
 * One threshold-triggered requirement within a jurisdiction's heat ruleset.
 *
 * <h2>Rules as data, not as code</h2>
 * It would be shorter to write {@code if (heatIndex >= 95) { ... }} inside a scheduler. It
 * would also be unreviewable: a safety officer cannot audit an if-statement buried three
 * classes deep, and adding a second state would mean editing scheduling logic rather than
 * adding a row.
 *
 * <p>Modelling each requirement as a value object means the full ruleset can be printed,
 * diffed, reviewed by someone who does not read Java, and returned over the API so a user
 * can see exactly which rule fired and why.
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
