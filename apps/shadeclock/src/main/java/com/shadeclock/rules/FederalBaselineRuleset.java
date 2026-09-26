package com.shadeclock.rules;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * The fallback used where a state has no enforceable heat standard of its own.
 *
 * <p><b>Guidance, not law.</b> There is no finalised federal heat standard, so employers in
 * the ~43 states without their own are covered only by the General Duty Clause and OSHA's
 * Heat National Emphasis Program. Marked {@link VerificationStatus#GUIDANCE_NOT_LAW}:
 * presenting these as legal obligations would be false, but presenting nothing would leave
 * most American outdoor workers with an app that shrugs.
 *
 * <p>One evidence-backed reason to think codified rules help: a George Washington / Harvard
 * study of OSHA injury data across 48 states found workers in states <em>with</em> heat
 * standards appear to have lower injury risk on hot days.
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
