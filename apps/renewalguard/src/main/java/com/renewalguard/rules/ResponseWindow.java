package com.renewalguard.rules;

import org.springframework.stereotype.Component;

/**
 * How long a person has to respond once their agency asks for documents.
 *
 * <h2>Why this assumes the shortest window rather than the typical one</h2>
 * Response windows vary by state. Reporting indicates that where documentation is required
 * to avoid termination, <b>14 states give enrollees only 10 days to respond</b>. A federal
 * Eligibility &amp; Enrollment rule would require at least 30 days where data indicates
 * ineligibility, but that provision is reported to be subject to a 10-year implementation
 * moratorium.
 *
 * <p><b>I do not know which 14 states.</b> That figure came from a single source and the
 * state list was not part of it. Inventing a plausible-looking list of states would be
 * fabrication, and fabrication in a tool people rely on for a deadline is worse than
 * admitting a gap.
 *
 * <p>So RenewalGuard does the defensible thing instead: it <b>assumes the shortest window
 * anyone is reported to get</b> ({@value #CONSERVATIVE_DEFAULT_DAYS} days) unless the user
 * tells it otherwise from their own notice. The asymmetry is the whole argument - assuming
 * 30 days when someone actually has 10 loses their coverage; assuming 10 when they have 30
 * just means they finish early.
 *
 * <p>⚠️ Both figures above are single-source and unverified against regulation text. They
 * shape the reminder cadence; they are never quoted to a user as legal fact.
 */
@Component
public class ResponseWindow {

    /**
     * The shortest reported response window, in days.
     *
     * <p>Used as the default because erring short is the safe direction.
     */
    public static final int CONSERVATIVE_DEFAULT_DAYS = 10;

    /** The longest window commonly discussed, used only to describe the range to users. */
    public static final int REPORTED_UPPER_DAYS = 30;

    /**
     * The response window to plan against for a case.
     *
     * @param userSuppliedDays the window stated on the user's own notice, or {@code null}
     *                         if they have not told us
     * @return the days to plan against, never less than 1
     */
    public int planningWindowDays(Integer userSuppliedDays) {
        if (userSuppliedDays == null || userSuppliedDays < 1) {
            return CONSERVATIVE_DEFAULT_DAYS;
        }
        return userSuppliedDays;
    }

    /**
     * Whether the planning window is an assumption rather than something the user told us.
     *
     * <p>Drives a visible "we assumed the shortest window - check your notice" prompt, so
     * the guess is never mistaken for knowledge.
     *
     * @param userSuppliedDays what the user supplied, if anything
     * @return {@code true} if we fell back to the conservative default
     */
    public boolean isAssumed(Integer userSuppliedDays) {
        return userSuppliedDays == null || userSuppliedDays < 1;
    }

    /**
     * A plain-language explanation of the assumption, for display.
     *
     * @param userSuppliedDays what the user supplied, if anything
     * @return the explanation, never {@code null}
     */
    public String explain(Integer userSuppliedDays) {
        if (!isAssumed(userSuppliedDays)) {
            return "Using the " + userSuppliedDays + "-day response window you entered from "
                    + "your notice.";
        }
        return "We assume you may get as little as " + CONSERVATIVE_DEFAULT_DAYS
                + " days to send documents, because some states allow only that. Your notice "
                + "may give you longer (up to about " + REPORTED_UPPER_DAYS + " days). "
                + "Check it and tell us, and we will adjust your reminders.";
    }
}
