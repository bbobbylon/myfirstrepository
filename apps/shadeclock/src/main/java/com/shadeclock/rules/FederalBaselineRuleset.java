package com.shadeclock.rules;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * The fallback used where a state has no enforceable heat standard of its own.
 *
 * <h2>This encodes guidance, not law</h2>
 * As of 2026 there is no finalised federal heat standard. OSHA's proposed
 * <em>Heat Injury and Illness Prevention in Outdoor and Indoor Work Settings</em> rule was
 * published 30 August 2024, its comment period closed January 2025 and hearings ran to July
 * 2025, but the rule stalled after the January 2025 regulatory freeze with no target
 * finalisation date.
 *
 * <p>Employers in the roughly 43 states without their own standard are therefore covered
 * only by the General Duty Clause and by OSHA's Heat National Emphasis Program - a revised
 * version of which was issued 10 April 2026, active for five years, targeting 55 high-risk
 * industries for inspection on any day the NWS issues a heat advisory.
 *
 * <p>So this ruleset is marked {@link VerificationStatus#GUIDANCE_NOT_LAW}. The thresholds
 * are drawn from the general 80°F / 90°F structure common to NWS heat guidance and to the
 * state standards, and are offered as sensible precautions. Presenting them as legal
 * obligations would be false; presenting nothing at all would be worse, because it would
 * leave the majority of American outdoor workers with an app that shrugs.
 *
 * <p>There is one genuinely evidence-backed reason to think codified rules help at all: a
 * George Washington University / Harvard study of OSHA injury data across 48 states found
 * that workers in states <em>with</em> heat standards appear to have a lower risk of injury
 * on hot days.
 *
 * @see <a href="https://www.osha.gov/heat-exposure/standards">OSHA - Heat Standards</a>
 */
@Component
public class FederalBaselineRuleset implements HeatRuleset {

    private static final String CITATION =
            "General precautions based on NWS heat guidance and OSHA's Heat National "
                    + "Emphasis Program (revised 10 April 2026). No finalised federal "
                    + "heat standard exists - these are NOT legal requirements.";

    /** {@inheritDoc} */
    @Override
    public String jurisdictionCode() {
        return "US-BASELINE";
    }

    /** {@inheritDoc} */
    @Override
    public String jurisdictionName() {
        return "United States (no state standard - general guidance only)";
    }

    /** {@inheritDoc} */
    @Override
    public String citation() {
        return CITATION;
    }

    /** {@inheritDoc} */
    @Override
    public VerificationStatus verificationStatus() {
        return VerificationStatus.GUIDANCE_NOT_LAW;
    }

    /** {@inheritDoc} */
    @Override
    public int acclimatizationDays() {
        return 14;
    }

    /** {@inheritDoc} */
    @Override
    public List<HeatRule> rules() {
        return List.of(
                new HeatRule(80,
                        "Provide drinking water and access to shade. Brief the crew on heat "
                                + "illness symptoms and what to do if someone shows them.",
                        0,
                        CITATION),

                new HeatRule(90,
                        "Increase observation. Build in regular rest in shade and encourage "
                                + "frequent water. Watch unacclimatised workers closely.",
                        10,
                        CITATION),

                new HeatRule(103,
                        "NWS Danger band. Strongly consider rescheduling strenuous work to "
                                + "cooler hours. Treat any symptom report as an emergency "
                                + "until ruled out.",
                        15,
                        CITATION),

                new HeatRule(125,
                        "NWS Extreme Danger band. Outdoor physical work should stop.",
                        30,
                        CITATION));
    }
}
