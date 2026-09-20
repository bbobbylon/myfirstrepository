package com.shadeclock.rules;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Looks up the {@link HeatRuleset} for a jurisdiction, falling back to general guidance.
 *
 * <p>Spring injects every {@code HeatRuleset} bean it finds, so adding a state means adding
 * one class - no edit here, and no edit to the scheduler. That is the payoff for modelling
 * rules as data.
 */
@Component
public class RulesetRegistry {

    /** Used when a jurisdiction has no ruleset of its own. */
    public static final String FALLBACK_CODE = "US-BASELINE";

    private final Map<String, HeatRuleset> byCode;
    private final HeatRuleset fallback;

    /**
     * @param rulesets every ruleset on the classpath, injected by Spring
     * @throws IllegalStateException if the fallback ruleset is missing, which would leave
     *         unknown jurisdictions with no rules at all
     */
    public RulesetRegistry(List<HeatRuleset> rulesets) {
        this.byCode = rulesets.stream().collect(Collectors.toMap(
                ruleset -> ruleset.jurisdictionCode().toUpperCase(Locale.ROOT),
                Function.identity()));

        this.fallback = byCode.get(FALLBACK_CODE);
        if (fallback == null) {
            // Fail at startup rather than at 2pm on a 100°F day. A missing fallback would
            // otherwise surface as an unknown jurisdiction silently getting no rules.
            throw new IllegalStateException(
                    "No fallback ruleset registered under code " + FALLBACK_CODE);
        }
    }

    /**
     * Finds the ruleset for a jurisdiction.
     *
     * <p>An unrecognised code returns the general-guidance fallback rather than throwing.
     * A crew in a state ShadeClock has not modelled still deserves water-and-shade advice;
     * refusing to answer would be the worse failure. The returned ruleset always declares
     * its own {@link VerificationStatus}, so the caller can tell the difference.
     *
     * @param jurisdictionCode the code to look up; {@code null} or unknown yields the fallback
     * @return a ruleset, never {@code null}
     */
    public HeatRuleset forJurisdiction(String jurisdictionCode) {
        if (jurisdictionCode == null || jurisdictionCode.isBlank()) {
            return fallback;
        }
        return byCode.getOrDefault(jurisdictionCode.toUpperCase(Locale.ROOT), fallback);
    }

    /**
     * Whether a jurisdiction has its own modelled ruleset.
     *
     * <p>Lets the UI say "we do not have <em>Texas</em> rules - showing general guidance"
     * rather than implying the fallback is that state's law.
     *
     * @param jurisdictionCode the code to test
     * @return {@code true} if a specific ruleset exists
     */
    public boolean hasSpecificRuleset(String jurisdictionCode) {
        return jurisdictionCode != null
                && byCode.containsKey(jurisdictionCode.toUpperCase(Locale.ROOT))
                && !FALLBACK_CODE.equalsIgnoreCase(jurisdictionCode);
    }

    /**
     * Every jurisdiction code ShadeClock knows about.
     *
     * @return the codes, never {@code null}
     */
    public List<String> knownJurisdictions() {
        return byCode.keySet().stream().sorted().toList();
    }
}
