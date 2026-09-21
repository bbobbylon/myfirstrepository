package com.renewalguard.rules;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.EnrollmentCategory;

/**
 * Works out how often a case renews, and therefore when the next deadline lands.
 *
 * <p>Cadence is a function of <b>two</b> things, not one: the enrolment category
 * <em>and</em> the date. P.L. 119-21 moves expansion adults to six-month renewals from
 * 1 January 2027; everyone else stays annual. Nobody has a habit built around a second
 * annual deadline yet, so the first six-month cycle is likely to produce a spike in
 * procedural terminations.
 */
@Component
public class RenewalCadence {

    /**
     * The date from which six-month renewals apply to expansion adults.
     *
     * <p>Renewals <em>scheduled on or after</em> this date fall under the new cadence;
     * states must comply beginning 31 December 2026.
     */
    public static final LocalDate SIX_MONTH_RULE_EFFECTIVE = LocalDate.of(2027, 1, 1);

    /** Months between renewals under the new expansion-adult rule. */
    public static final int SIX_MONTH_CADENCE = 6;

    /** Months between renewals on the standard annual cycle. */
    public static final int ANNUAL_CADENCE = 12;

    /**
     * How many months apart this case's renewals fall.
     *
     * @param category  the enrolment category
     * @param renewalOn the renewal date being considered
     * @return {@link #SIX_MONTH_CADENCE} or {@link #ANNUAL_CADENCE}
     */
    public int cadenceMonths(EnrollmentCategory category, LocalDate renewalOn) {
        if (category != null
                && category.subjectToSixMonthRenewals()
                && renewalOn != null
                && !renewalOn.isBefore(SIX_MONTH_RULE_EFFECTIVE)) {
            return SIX_MONTH_CADENCE;
        }
        return ANNUAL_CADENCE;
    }

    /**
     * Projects the renewal after the one currently recorded.
     *
     * <p>Shown to users so a six-month cadence is visible <em>before</em> it surprises them,
     * rather than arriving as an unexplained second letter in a year they expected one.
     *
     * @param benefitCase the case to project
     * @return the next renewal date after the recorded one
     */
    public LocalDate projectFollowingRenewal(BenefitCase benefitCase) {
        int months = cadenceMonths(benefitCase.category(), benefitCase.renewalDueOn());
        return benefitCase.renewalDueOn().plusMonths(months);
    }

    /**
     * Whether this case's cadence changes because of the 2027 rule.
     *
     * <p>Used to show a one-off explanatory note. A person whose renewal frequency is about
     * to double deserves to be told that in advance, in plain words, rather than discovering
     * it from an unexpected notice.
     *
     * @param benefitCase the case to test
     * @return {@code true} if this case is an expansion adult whose next renewal falls under
     *         the new rule
     */
    public boolean affectedBySixMonthChange(BenefitCase benefitCase) {
        return benefitCase.category().subjectToSixMonthRenewals()
                && !projectFollowingRenewal(benefitCase).isBefore(SIX_MONTH_RULE_EFFECTIVE);
    }
}
