package com.renewalguard.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.BenefitProgram;
import com.renewalguard.domain.EnrollmentCategory;

/**
 * Tests for the 2027 six-month renewal change.
 *
 * <p>This is the most consequential logic in RenewalGuard, because it is the part most likely
 * to be silently wrong in a way nobody notices until January 2027 - when a quarter of
 * enrollees start renewing twice as often and the app is still quietly projecting annual
 * deadlines for them.
 */
class RenewalCadenceTest {

    private final RenewalCadence cadence = new RenewalCadence();

    private BenefitCase caseFor(EnrollmentCategory category, LocalDate renewalDueOn) {
        return new BenefitCase("c1", "u1", BenefitProgram.MEDICAID, "CA",
                category, renewalDueOn, null, null);
    }

    @ParameterizedTest(name = "{0} renewing {1} -> {2} months")
    @CsvSource({
            // Expansion adults: annual before the rule, six-monthly from 2027
            "EXPANSION_ADULT,      2026-11-01, 12",
            "EXPANSION_ADULT,      2026-12-31, 12",
            "EXPANSION_ADULT,      2027-01-01,  6",
            "EXPANSION_ADULT,      2027-06-01,  6",
            // Everyone else stays annual, before and after
            "CHILD,                2027-06-01, 12",
            "PREGNANT,             2027-06-01, 12",
            "AGED_BLIND_DISABLED,  2027-06-01, 12",
            "UNKNOWN,              2027-06-01, 12"
    })
    @DisplayName("cadence depends on BOTH the category and the date")
    void cadenceDependsOnCategoryAndDate(EnrollmentCategory category,
                                          LocalDate renewalOn,
                                          int expectedMonths) {
        assertThat(cadence.cadenceMonths(category, renewalOn)).isEqualTo(expectedMonths);
    }

    @Test
    @DisplayName("the effective date boundary is exact")
    void boundaryIsExact() {
        // 31 Dec 2026 is still annual; 1 Jan 2027 is not. An off-by-one here would give a
        // whole cohort the wrong next deadline.
        assertThat(cadence.cadenceMonths(EnrollmentCategory.EXPANSION_ADULT,
                LocalDate.of(2026, 12, 31))).isEqualTo(12);
        assertThat(cadence.cadenceMonths(EnrollmentCategory.EXPANSION_ADULT,
                RenewalCadence.SIX_MONTH_RULE_EFFECTIVE)).isEqualTo(6);
    }

    @Test
    @DisplayName("projects the following renewal using the right cadence")
    void projectsFollowingRenewal() {
        BenefitCase expansionAdult = caseFor(EnrollmentCategory.EXPANSION_ADULT,
                LocalDate.of(2027, 3, 1));
        BenefitCase child = caseFor(EnrollmentCategory.CHILD, LocalDate.of(2027, 3, 1));

        assertThat(cadence.projectFollowingRenewal(expansionAdult))
                .isEqualTo(LocalDate.of(2027, 9, 1));
        assertThat(cadence.projectFollowingRenewal(child))
                .isEqualTo(LocalDate.of(2028, 3, 1));
    }

    @Test
    @DisplayName("a household can have two people on different cadences from 2027")
    void householdCanSplit() {
        // The scenario that makes this worth software: same programme, same state, same
        // agency, different renewal rhythms.
        BenefitCase parent = caseFor(EnrollmentCategory.EXPANSION_ADULT, LocalDate.of(2027, 4, 1));
        BenefitCase child = caseFor(EnrollmentCategory.CHILD, LocalDate.of(2027, 4, 1));

        assertThat(cadence.projectFollowingRenewal(parent))
                .isNotEqualTo(cadence.projectFollowingRenewal(child));
    }

    @Test
    @DisplayName("flags a case whose frequency is about to double")
    void flagsAffectedCases() {
        // Deserves a plain-language warning IN ADVANCE, not an unexplained second notice.
        assertThat(cadence.affectedBySixMonthChange(
                caseFor(EnrollmentCategory.EXPANSION_ADULT, LocalDate.of(2027, 2, 1)))).isTrue();
        assertThat(cadence.affectedBySixMonthChange(
                caseFor(EnrollmentCategory.CHILD, LocalDate.of(2027, 2, 1)))).isFalse();
    }

    @Test
    @DisplayName("an expansion adult renewing in 2026 is still flagged if the NEXT one crosses")
    void flagsWhenTheFollowingRenewalCrossesTheBoundary() {
        // Renewal in Aug 2026 -> next would be Aug 2027, which is under the new rule.
        // Warning them now is the whole point of warning in advance.
        BenefitCase crossing = caseFor(EnrollmentCategory.EXPANSION_ADULT,
                LocalDate.of(2026, 8, 1));

        assertThat(cadence.affectedBySixMonthChange(crossing)).isTrue();
    }

    @Test
    @DisplayName("only expansion adults are subject to the change")
    void onlyExpansionAdultsAffected() {
        assertThat(EnrollmentCategory.EXPANSION_ADULT.subjectToSixMonthRenewals()).isTrue();
        for (EnrollmentCategory other : new EnrollmentCategory[]{
                EnrollmentCategory.CHILD, EnrollmentCategory.PREGNANT,
                EnrollmentCategory.AGED_BLIND_DISABLED, EnrollmentCategory.UNKNOWN}) {
            assertThat(other.subjectToSixMonthRenewals()).isFalse();
        }
    }
}
