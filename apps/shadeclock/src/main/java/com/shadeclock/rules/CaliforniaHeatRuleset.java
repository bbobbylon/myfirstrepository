package com.shadeclock.rules;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * California's outdoor heat illness prevention requirements.
 *
 * <p>California is implemented first because it is the oldest and best documented of the
 * state standards, which makes it the most useful one to model the abstraction against.
 *
 * <h2>⚠️ READ THIS BEFORE ANY CREW RELIES ON THESE NUMBERS</h2>
 * Every threshold below is {@link VerificationStatus#UNVERIFIED_SECONDARY_SOURCE}. They were
 * assembled from compliance summaries and news coverage, <b>not</b> from the text of
 * 8 CCR §3395, because the build environment's egress policy could not reach the California
 * regulations site to read it.
 *
 * <p>In particular, the treatment of high-heat procedures at 95°F is the one most likely to
 * be wrong: California's high-heat provisions differ by industry (agriculture carries a
 * specific preventative cool-down cadence that other sectors do not), and this ruleset
 * flattens that into a single conservative cadence. <b>Flattening in the conservative
 * direction is deliberate</b> - erring towards more rest is the safe direction for a
 * screening tool - but it means this must not be presented as a compliance determination.
 *
 * <p>Verifying these against the regulation and flipping the status to
 * {@link VerificationStatus#VERIFIED_AGAINST_REGULATION} is the single highest-value task
 * outstanding on ShadeClock.
 *
 * @see <a href="https://www.dir.ca.gov/title8/3395.html">8 CCR §3395 - Heat Illness
 *      Prevention in Outdoor Places of Employment</a>
 */
@Component
public class CaliforniaHeatRuleset implements HeatRuleset {

    private static final String CITATION =
            "California Code of Regulations, Title 8, §3395 (Heat Illness Prevention in "
                    + "Outdoor Places of Employment) - NOT verified against the regulation text";

    /** {@inheritDoc} */
    @Override
    public String jurisdictionCode() {
        return "CA";
    }

    /** {@inheritDoc} */
    @Override
    public String jurisdictionName() {
        return "California";
    }

    /** {@inheritDoc} */
    @Override
    public String citation() {
        return CITATION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deliberately {@link VerificationStatus#UNVERIFIED_SECONDARY_SOURCE} - see the
     * class-level warning. Do not change this without actually reading §3395.
     */
    @Override
    public VerificationStatus verificationStatus() {
        return VerificationStatus.UNVERIFIED_SECONDARY_SOURCE;
    }

    /** {@inheritDoc} */
    @Override
    public int acclimatizationDays() {
        // Commonly reported as a two-week window for new hires.
        return 14;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Ordered by ascending trigger so the list reads like the regulation does, and so a
     * reviewer can scan thresholds top to bottom.
     */
    @Override
    public List<HeatRule> rules() {
        return List.of(
                new HeatRule(80,
                        "Provide shade that can accommodate workers on recovery or rest "
                                + "periods, and allow access to it. Provide fresh, pure, "
                                + "suitably cool drinking water free of charge (1 quart per "
                                + "worker per hour).",
                        0,
                        CITATION),

                new HeatRule(80,
                        "Maintain a written Heat Illness Prevention Plan and train workers "
                                + "and supervisors on heat illness signs and emergency response.",
                        0,
                        CITATION),

                new HeatRule(95,
                        "High-heat procedures apply: maintain effective two-way communication "
                                + "with workers, observe workers for signs of heat illness, "
                                + "remind workers to drink water, and hold a pre-shift meeting "
                                + "on heat illness prevention.",
                        10,
                        CITATION),

                new HeatRule(105,
                        "Conditions are in the NWS Danger band. Consider rescheduling "
                                + "non-essential work, increase observation frequency, and "
                                + "treat any symptom report as an emergency until ruled out.",
                        15,
                        CITATION));
    }
}
