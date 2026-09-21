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
 * @param userId              the owner
 * @param program             which programme this case is under
 * @param stateCode           two-letter state code, e.g. {@code "CA"}
 * @param category            eligibility group, which drives renewal cadence from 2027
 * @param renewalDueOn        the date the renewal must be completed by
 * @param noticeReceivedOn    when the renewal notice arrived, or {@code null} if not yet
 * @param addressConfirmedOn  when the user last confirmed their address with the agency,
 *                            or {@code null} if never
 */
public record BenefitCase(
        String id,
        String userId,
        BenefitProgram program,
        String stateCode,
        EnrollmentCategory category,
        LocalDate renewalDueOn,
        LocalDate noticeReceivedOn,
        LocalDate addressConfirmedOn) {

    /**
     * Validates and defaults the fields the reminder logic depends on.
     *
     * @throws IllegalArgumentException if {@code renewalDueOn} is missing, which would make
     *         every downstream date calculation meaningless
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
