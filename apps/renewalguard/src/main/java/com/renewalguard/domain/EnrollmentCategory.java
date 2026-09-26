package com.renewalguard.domain;

/**
 * Which Medicaid eligibility group a person is enrolled through.
 *
 * <p><b>P.L. 119-21 moves Medicaid expansion adults to six-month renewals</b> for renewals
 * scheduled on or after <b>1 January 2027</b>; CMS issued guidance on 6 March 2026. Children,
 * pregnant women, seniors and disabled enrollees stay annual - reporting puts the affected
 * group at a little over a quarter of enrollees.
 *
 * <p>So from 2027 one household can hold two people, same programme and agency, with
 * deadlines arriving at different rates. That is how a letter gets missed - and a missed
 * letter is how roughly <b>70% of Medicaid terminations</b> already happen.
 *
 * <p>⚠️ Sources cite the provision as both "Section 44108" and "Section 71107" of
 * P.L. 119-21 (both describing an amendment to SSA §1902(e)(14)). The substance is
 * consistently reported; the section number is not. Verify before quoting a section number
 * to anyone.
 *
 * @see <a href="https://www.medicaid.gov/federal-policy-guidance/downloads/smd26001.pdf">CMS
 *      State Medicaid Director letter SMD# 26-001 (6 March 2026)</a>
 * @see <a href="https://ccf.georgetown.edu/2026/03/06/cms-releases-guidance-on-6-month-medicaid-renewals-for-expansion-adults/">Georgetown
 *      CCF - CMS Releases Guidance on 6-Month Medicaid Renewals for Expansion Adults</a>
 */
public enum EnrollmentCategory {

    /**
     * ACA expansion adults - broadly ages 19-64 with income up to 138% of the federal
     * poverty level. Moves to six-month renewals from 2027.
     */
    EXPANSION_ADULT("ACA expansion adult", true),

    /** Children. Remains on an annual cycle. */
    CHILD("Child", false),

    /** Pregnancy-related coverage. Remains on an annual cycle. */
    PREGNANT("Pregnancy coverage", false),

    /** Aged, blind or disabled pathways. Remain on an annual cycle. */
    AGED_BLIND_DISABLED("Aged, blind or disabled", false),

    /**
     * Anything else, or not yet known.
     *
     * <p>Treated as annual, and flagged to the user so they can correct it. Guessing
     * "expansion adult" for an unknown category would invent a deadline six months early;
     * guessing annual risks one late. Annual plus a visible prompt is the honest middle:
     * we say we do not know rather than silently picking.
     */
    UNKNOWN("Not specified", false);

    private final String label;
    private final boolean subjectToSixMonthRenewals;

    EnrollmentCategory(String label, boolean subjectToSixMonthRenewals) {
        this.label = label;
        this.subjectToSixMonthRenewals = subjectToSixMonthRenewals;
    }

    /**
     * A human-readable label for this category.
     *
     * @return the label, never {@code null}
     */
    public String label() {
        return label;
    }

    /**
     * Whether P.L. 119-21's six-month renewal requirement reaches this category.
     *
     * @return {@code true} only for {@link #EXPANSION_ADULT}
     */
    public boolean subjectToSixMonthRenewals() {
        return subjectToSixMonthRenewals;
    }

    /**
     * Whether ShadeClock-style "we are not sure" messaging should be shown for this category.
     *
     * @return {@code true} if the category was not supplied
     */
    public boolean isUnspecified() {
        return this == UNKNOWN;
    }
}
