package com.safeword.scam;

import java.util.List;
import java.util.Set;

/**
 * What SafeWord concluded about a call in progress.
 *
 * @param score          summed weight of the observed tactics
 * @param level          the resulting recommendation level
 * @param observed       the tactics reported
 * @param likelyPatterns scam patterns that fit, best first
 * @param headline       the one line to show largest on screen
 */
public record RiskAssessment(
        int score,
        RiskLevel level,
        Set<PressureSignal> observed,
        List<ScamPattern> likelyPatterns,
        String headline) {

    /**
     * Defensive-copies the collections.
     */
    public RiskAssessment {
        observed = observed == null ? Set.of() : Set.copyOf(observed);
        likelyPatterns = likelyPatterns == null ? List.of() : List.copyOf(likelyPatterns);
    }

    /**
     * How confident SafeWord is, stated honestly.
     *
     * <p>Deliberately never claims certainty. This is a checklist scored from what a
     * frightened person ticked on a phone, not a determination. Overstating it would earn
     * exactly the misplaced trust that AI-voice scams already exploit.
     *
     * @return the confidence caveat, never {@code null}
     */
    public String confidenceCaveat() {
        return "This is based only on what you told us about the call. It is not a "
                + "certainty either way. When in doubt, hang up and call back on a number "
                + "you already have - that costs you nothing.";
    }
}
