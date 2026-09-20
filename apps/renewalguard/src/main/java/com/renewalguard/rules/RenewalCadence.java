package com.renewalguard.rules;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.EnrollmentCategory;

/**
 * Works out how often a case renews, and therefore when the next deadline lands.
 *
 * <h2>The 2027 change this exists to handle</h2>
 * Medicaid renewal used to be effectively annual for everyone. Public Law 119-21 changes
 * that: states must redetermine <b>expansion adults every six months</b>, for renewals
 * scheduled <b>on or after 1 January 2027</b>. Everyone else stays annual.
 *
 * <p>The consequence for this class is that cadence is a function of two things, not one -
 * the enrolment category <em>and</em> the date. A household with a parent on expansion
 * coverage and a child on children's coverage will, from 2027, have deadlines arriving at
 * different rates from the same agency.
 *
 * <p><b>The analogy:</b> it is the difference between a magazine subscription that renews
 * every January and one that renews every January <em>and</em> July. Miss the second and you
 * are just as uncovered as missing the first - but nobody has a habit built around it yet,
 * because it did not exist last year. The first six-month cycle in 2027 is likely to produce
 * a spike in procedural terminations for exactly that reason.
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
