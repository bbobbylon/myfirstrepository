package com.renewalguard.domain;

/**
 * How urgent a renewal deadline is, and what the person should do about it.
 *
 * <p>Shaped by how little time states give: where documents are required,
 * <b>14 states allow only 10 days to respond</b>. A ladder built on a comfortable 30-day
 * assumption would fire its first real warning after the window had half closed. So it
 * starts early and escalates hard - gentle at 60 days, firm at 30, daily inside the last 10.
 *
 * <p>⚠️ The "14 states / 10 days" figure and the 10-day notice requirement are each from a
 * single source and could not be confirmed against regulation text from this environment.
 * They inform the ladder's shape rather than any legal claim made to a user.
 */
public enum RenewalUrgency {

    /** Inside the final 10 days, or already past due. Daily reminders. */
    CRITICAL("Act today", "Your renewal is due now. A missed deadline can end coverage even "
            + "if you still qualify."),

    /** 11-30 days out. Firm reminders. */
    HIGH("Act this week", "Gather your documents and complete your renewal now, before the "
            + "window gets tight."),

    /** 31-60 days out. A planning nudge. */
    MEDIUM("Start preparing", "Check your address is up to date and start gathering "
            + "documents."),

    /** More than 60 days out. Informational only. */
    WATCH("No action needed yet", "We will remind you as your renewal date approaches.");

    private final String headline;
    private final String guidance;

    RenewalUrgency(String headline, String guidance) {
        this.headline = headline;
        this.guidance = guidance;
    }

    /**
     * A short imperative headline for this urgency level.
     *
     * @return the headline, never {@code null}
     */
    public String headline() {
        return headline;
    }

    /**
     * Plain-language guidance matching this urgency.
     *
     * <p>Every string here describes a <em>process</em> step - update an address, gather
     * documents, submit the form. None of them says anything about whether the person
     * qualifies. That boundary is what keeps RenewalGuard a deadline tracker rather than
     * something pretending to make eligibility determinations, which are the state agency's
     * legal function.
     *
     * @return the guidance, never {@code null}
     */
    public String guidance() {
        return guidance;
    }

    /**
     * Classifies days remaining into an urgency level.
     *
     * @param daysUntilDue days from today to the renewal deadline; negative if past
     * @return the matching urgency, never {@code null}
     */
    public static RenewalUrgency fromDaysUntilDue(long daysUntilDue) {
        // Past due is CRITICAL, not WATCH - unlike RefillRadar, where a passed date usually
        // meant stale data. Here a passed renewal date is the actual emergency: coverage may
        // be closing or already closed, and reinstatement is often still possible if acted
        // on quickly.
        if (daysUntilDue <= 10) {
            return CRITICAL;
        }
        if (daysUntilDue <= 30) {
            return HIGH;
        }
        if (daysUntilDue <= 60) {
            return MEDIUM;
        }
        return WATCH;
    }
}
