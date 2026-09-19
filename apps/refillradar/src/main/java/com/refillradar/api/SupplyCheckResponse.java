package com.refillradar.api;

import java.time.LocalDate;
import java.util.List;

/**
 * The result of checking one user's medications against the FDA shortage feed.
 *
 * <h2>Why "no matches" is an explicit, positive result</h2>
 * {@code checkedAt}, {@code source} and {@code medicationsChecked} are always populated,
 * even when {@code matches} is empty. An empty response body would be ambiguous: it could
 * mean "we checked and you are fine" or "the sync failed and we know nothing". Those are
 * very different messages to give someone who depends on a medicine, and conflating them is
 * how software produces false reassurance.
 *
 * <p>This is the API-level expression of the same principle that makes
 * {@code ShortageStatus.UNKNOWN} count as active: uncertainty must be visible.
 *
 * @param userId             the user this check ran for
 * @param checkedAt          the date the check was evaluated against
 * @param source             provenance of the shortage data
 * @param medicationsChecked how many of the user's medications were examined
 * @param shortagesScanned   how many FDA records were scanned
 * @param matches            affected medications, most urgent first; empty means none found
 */
public record SupplyCheckResponse(
        String userId,
        LocalDate checkedAt,
        String source,
        int medicationsChecked,
        int shortagesScanned,
        List<MatchSummary> matches) {

    /**
     * One affected medication, flattened for JSON.
     *
     * @param medication        the user's name for it
     * @param fdaRecord         the matching FDA product name
     * @param matchedOn         the normalised token that linked them, for auditability
     * @param runOutDate        projected run-out date
     * @param daysRemaining     days until that date; negative if passed
     * @param risk              urgency level
     * @param recommendedAction the non-clinical next step
     * @param shortageReason    the FDA's stated cause, if any
     * @param uncertainStatus   whether the FDA status could not be interpreted
     */
    public record MatchSummary(
            String medication,
            String fdaRecord,
            String matchedOn,
            LocalDate runOutDate,
            long daysRemaining,
            String risk,
            String recommendedAction,
            String shortageReason,
            boolean uncertainStatus) {
    }
}
