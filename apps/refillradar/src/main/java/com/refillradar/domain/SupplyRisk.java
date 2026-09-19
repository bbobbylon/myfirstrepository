package com.refillradar.domain;

/**
 * How urgent a supply problem is for one specific patient.
 *
 * <p>Urgency is a function of <em>time remaining</em>, not of how severe the shortage is
 * nationally. A nationwide shortage of a drug you refilled yesterday is a watch item; a
 * regional blip on a drug you finish on Thursday is an emergency. The patient's calendar,
 * not the FDA's severity language, drives this scale.
 *
 * <p>The thresholds below are product decisions, not clinical ones, and are stated in one
 * place so they can be reviewed and changed deliberately.
 */
public enum SupplyRisk {

    /** Seven days or fewer of supply remaining. Act today. */
    CRITICAL("Contact your prescriber or pharmacist today."),

    /** Eight to fourteen days remaining. Enough time to book an appointment. */
    HIGH("Contact your prescriber this week."),

    /** Fifteen to thirty days remaining. Raise it at your next contact. */
    MEDIUM("Mention this at your next pharmacy or prescriber contact."),

    /** More than thirty days remaining, or already out. Informational. */
    WATCH("No action needed yet - we will keep watching.");

    private final String recommendedAction;

    SupplyRisk(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    /**
     * A short, non-clinical instruction matching this urgency level.
     *
     * <p>Every string here routes the patient to a human - a prescriber or a pharmacist -
     * and none suggests a course of action with the medicine itself. That boundary is what
     * keeps RefillRadar an information tool rather than something that offers medical
     * advice, and it is a line the copy must never cross.
     *
     * @return the recommended next step
     */
    public String recommendedAction() {
        return recommendedAction;
    }

    /**
     * Classifies remaining days of supply into an urgency level.
     *
     * @param daysRemaining days until the projected run-out date; may be negative if the
     *                      supply is already exhausted
     * @return the matching risk level, never {@code null}
     */
    public static SupplyRisk fromDaysRemaining(long daysRemaining) {
        // Already out of supply. Ranked WATCH rather than CRITICAL on purpose: if the date
        // has passed, either they already refilled (so our data is stale and an alarming
        // alert would be noise) or they have a problem no notification can now prevent.
        if (daysRemaining < 0) {
            return WATCH;
        }
        if (daysRemaining <= 7) {
            return CRITICAL;
        }
        if (daysRemaining <= 14) {
            return HIGH;
        }
        if (daysRemaining <= 30) {
            return MEDIUM;
        }
        return WATCH;
    }
}
