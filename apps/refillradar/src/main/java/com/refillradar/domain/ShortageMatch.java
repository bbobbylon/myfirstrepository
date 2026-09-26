package com.refillradar.domain;

import java.time.LocalDate;

/**
 * The output of the engine: one of a user's medications, paired with an FDA shortage record
 * that appears to affect it, plus the timing that makes it urgent or not.
 *
 * @param medication      the user's medication that matched
 * @param shortage        the FDA record it matched against
 * @param runOutDate      the projected date this user's supply is exhausted
 * @param daysRemaining   days from the evaluation date to {@code runOutDate}; negative if past
 * @param risk            urgency for this patient
 * @param matchedOn       the normalised token that caused the match, retained so an alert can
 *                        show its working and a human can audit a false positive
 */
public record ShortageMatch(
        Medication medication,
        ShortageRecord shortage,
        LocalDate runOutDate,
        long daysRemaining,
        SupplyRisk risk,
        String matchedOn) {

    /**
     * Whether this match should trigger an outbound alert.
     *
     * <p>Alerting on every match would mean messaging users about shortages of drugs they
     * refilled last week, which trains people to ignore the app - and an ignored alert is
     * worth less than no alert at all, because it also carries false reassurance.
     *
     * @return {@code true} when urgency is {@link SupplyRisk#MEDIUM} or higher
     */
    public boolean warrantsAlert() {
        return risk != SupplyRisk.WATCH;
    }

    /**
     * Whether this match rests on an FDA status string we could not interpret.
     *
     * <p>Surfaced in user-facing copy so that uncertainty is visible rather than hidden
     * behind confident-sounding wording.
     *
     * @return {@code true} if the underlying status was unrecognised
     */
    public boolean hasUncertainStatus() {
        return !shortage.status().isConfident();
    }
}
