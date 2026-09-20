package com.renewalguard.api;

import java.time.LocalDate;
import java.util.List;

import com.renewalguard.checklist.RequiredDocument;

/**
 * Everything a user needs to know about one case right now.
 *
 * <p>Like the other two apps, this always carries its own caveats: the assumed response
 * window, the reminder that their own notice is authoritative, and - where relevant - the
 * warning that their renewal frequency is about to double under the 2027 rule.
 *
 * @param caseId               the case
 * @param program              programme label
 * @param stateCode            state
 * @param category             eligibility group label
 * @param checkedOn            the date this was evaluated
 * @param renewalDueOn         the deadline
 * @param daysUntilDue         days remaining; negative if past
 * @param urgency              urgency level name
 * @param headline             short imperative for this urgency
 * @param guidance             process guidance for this urgency
 * @param documentsReadyBy     work-backwards date for having paperwork in hand
 * @param needsAddressCheck    whether to prompt about address
 * @param remindingToday       whether today is a reminder day
 * @param reminderMilestones   the full ladder, in days-before
 * @param nextRenewalAfterThis projected following deadline
 * @param cadenceMonths        months between renewals for this case
 * @param sixMonthChangeApplies whether the 2027 expansion-adult rule reaches this case
 * @param windowExplanation    the response-window caveat
 * @param documents            the checklist
 * @param caveats              standing honesty notes
 */
public record RenewalStatusResponse(
        String caseId,
        String program,
        String stateCode,
        String category,
        LocalDate checkedOn,
        LocalDate renewalDueOn,
        long daysUntilDue,
        String urgency,
        String headline,
        String guidance,
        LocalDate documentsReadyBy,
        boolean needsAddressCheck,
        boolean remindingToday,
        List<Integer> reminderMilestones,
        LocalDate nextRenewalAfterThis,
        int cadenceMonths,
        boolean sixMonthChangeApplies,
        String windowExplanation,
        List<RequiredDocument> documents,
        List<String> caveats) {
}
