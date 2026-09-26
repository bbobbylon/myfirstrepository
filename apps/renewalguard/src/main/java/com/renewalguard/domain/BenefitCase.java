package com.renewalguard.domain;

import java.time.LocalDate;

/**
 * One person's enrolment in one benefit programme, with the dates a renewal turns on.
 *
 * <p><b>No SSN, income, household composition or immigration status.</b> This tracks
 * <em>when</em> a renewal is due and <em>what paperwork</em> it needs; it never evaluates
 * whether someone qualifies, so it has no business holding eligibility data. A breach here
 * would fall on the population least able to absorb it, and the most secure data is the data
 * you chose not to collect.
 *
 * @param id                  stable identifier
 * @param userId              the owner's account id, taken from the session and never from
 *                            a request payload - see the class comment on the controller
 * @param program             which programme this case is under
 * @param stateCode           two-letter state code, e.g. {@code "CA"}
 * @param category            eligibility group, which drives renewal cadence from 2027
 * @param renewalDueOn        the date the renewal must be completed by
 * @param noticeReceivedOn    when the renewal notice arrived, or {@code null} if not yet
 * @param addressConfirmedOn  when the user last confirmed their address with the agency,
 *                            or {@code null} if never
 * @param responseWindowDays  days to respond as printed on the user's own notice, or
 *                            {@code null} when they have not told us. In v0.1 this lived in
 *                            a map on the controller, so it was lost on restart while the
 *                            case it described survived, and two replicas disagreed about
 *                            the same case. It is part of the case, so it belongs here.
 */
public record BenefitCase(
        String id,
        String userId,
        BenefitProgram program,
        String stateCode,
        EnrollmentCategory category,
        LocalDate renewalDueOn,
        LocalDate noticeReceivedOn,
        LocalDate addressConfirmedOn,
        Integer responseWindowDays) {

    /**
     * Validates and defaults the fields the reminder logic depends on.
     *
     * @throws IllegalArgumentException if {@code renewalDueOn} is missing, which would make
     *         every downstream date calculation meaningless, or if a supplied
     *         {@code responseWindowDays} is not positive
     */
    public BenefitCase {
        if (renewalDueOn == null) {
            throw new IllegalArgumentException("renewalDueOn is required - it is the whole point");
        }
        if (program == null) {
            program = BenefitProgram.MEDICAID;
        }
        if (category == null) {
            category = EnrollmentCategory.UNKNOWN;
        }
        // A zero or negative window would make documentsReadyBy project a preparation
        // deadline on or after the date it is supposed to precede - advice that reads as
        // confident and is arithmetically impossible. The database enforces this too.
        if (responseWindowDays != null && responseWindowDays <= 0) {
            throw new IllegalArgumentException(
                    "responseWindowDays must be positive if supplied, was " + responseWindowDays);
        }
    }

    /**
     * Builds a case whose notice window is unknown.
     *
     * <p>Most cases are this one: the user is tracking a deadline they know about but has not
     * received (or has not typed in) the notice that states how long they get to respond.
     * {@link com.renewalguard.rules.ResponseWindow} then assumes the shortest plausible
     * window rather than a comfortable one, so the omission errs towards prompting early.
     *
     * <p>An overload rather than a nullable argument at every call site, because "no window
     * stated" is the normal case and should not have to be spelled as {@code null}.
     *
     * @param id                 stable identifier
     * @param userId             the owner's account id
     * @param program            which programme
     * @param stateCode          two-letter state code
     * @param category           eligibility group
     * @param renewalDueOn       the renewal deadline
     * @param noticeReceivedOn   when the notice arrived, or {@code null}
     * @param addressConfirmedOn when the address was last confirmed, or {@code null}
     */
    public BenefitCase(String id, String userId, BenefitProgram program, String stateCode,
                       EnrollmentCategory category, LocalDate renewalDueOn,
                       LocalDate noticeReceivedOn, LocalDate addressConfirmedOn) {
        this(id, userId, program, stateCode, category, renewalDueOn, noticeReceivedOn,
                addressConfirmedOn, null);
    }

    /**
     * Whether the user has confirmed their address recently enough to trust the post.
     *
     * <p>Undelivered mail is one of the largest single contributors to procedural
     * termination: the notice goes to an old address, nobody responds, and a case closes for
     * someone who never stopped qualifying. Prompting "have you moved?" is close to trivial
     * to build and is plausibly the highest-leverage feature in this entire application.
     *
     * @param today         the day to assess against
     * @param staleAfterDays how long a confirmation stays good for
     * @return {@code true} if the address has never been confirmed, or not recently enough
     */
    public boolean needsAddressCheck(LocalDate today, int staleAfterDays) {
        if (addressConfirmedOn == null) {
            return true;
        }
        return addressConfirmedOn.plusDays(staleAfterDays).isBefore(today);
    }

    /**
     * Whether RenewalGuard actually models this case's renewal cadence.
     *
     * @return {@code true} where the programme's rules are implemented
     */
    public boolean cadenceIsModelled() {
        return program.renewalRulesModelled();
    }
}
