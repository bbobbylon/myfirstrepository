package com.shadeclock.rules;

import java.util.List;
import java.util.Optional;

/**
 * The heat-safety requirements that apply in one jurisdiction.
 *
 * <h2>Why a patchwork needs an abstraction</h2>
 * As of 2026 there is <b>no finalised federal heat standard</b>. OSHA proposed one in August
 * 2024; the rule stalled after the January 2025 regulatory freeze with no target date. In
 * its place sits a Heat National Emphasis Program (targeted inspections on days the NWS
 * issues a heat advisory) plus roughly seven states with their own enforceable standards -
 * commonly counted as California, Colorado, Maryland, Minnesota, Nevada, Oregon and
 * Washington.
 *
 * <p>A contractor operating across three states therefore faces three rulebooks and no
 * federal baseline. That is precisely the sort of messy conditional bookkeeping software
 * handles well and tired humans handle badly, and it is the reason this interface exists
 * rather than a single hard-coded rule list.
 *
 * <p><b>Every implementation must declare a {@link VerificationStatus}.</b> See that enum
 * for why the doubt is modelled rather than hidden.
 */
public interface HeatRuleset {

    /**
     * Short jurisdiction key, e.g. {@code "CA"} or {@code "US-BASELINE"}.
     *
     * @return the key used to select this ruleset, never {@code null}
     */
    String jurisdictionCode();

    /**
     * Human-readable jurisdiction name.
     *
     * @return the name, never {@code null}
     */
    String jurisdictionName();

    /**
     * Where this ruleset's requirements come from.
     *
     * @return a citation a reader can look up, never {@code null}
     */
    String citation();

    /**
     * How confident we are that these thresholds match the regulation.
     *
     * @return the verification status, never {@code null}
     */
    VerificationStatus verificationStatus();

    /**
     * Every requirement in this ruleset, ascending by trigger threshold.
     *
     * @return the rules, never {@code null}
     */
    List<HeatRule> rules();

    /**
     * The days a newly-exposed worker must be acclimatised over under this jurisdiction.
     *
     * @return the acclimatisation period in days, or {@code 0} where the jurisdiction
     *         does not specify one
     */
    int acclimatizationDays();

    /**
     * Every rule triggered at the given heat index.
     *
     * @param heatIndexF the heat index to evaluate
     * @return triggered rules in ascending threshold order, never {@code null}
     */
    default List<HeatRule> applicableRules(double heatIndexF) {
        return rules().stream().filter(rule -> rule.appliesAt(heatIndexF)).toList();
    }

    /**
     * The strictest rest cadence triggered at this heat index.
     *
     * <p>"Strictest" means the largest rest requirement, not the highest threshold: where
     * several rules apply, the one demanding the most rest governs. Taking the most recently
     * triggered rule instead would be a subtle and dangerous bug if the rules were ever
     * listed out of order.
     *
     * @param heatIndexF the heat index to evaluate
     * @return the governing rest requirement, or empty if no triggered rule mandates rest
     */
    default Optional<HeatRule> governingRestRule(double heatIndexF) {
        return applicableRules(heatIndexF).stream()
                .filter(HeatRule::mandatesRest)
                .max((a, b) -> Integer.compare(a.restMinutesPerHour(), b.restMinutesPerHour()));
    }

    /**
     * Mandated rest minutes per working hour at this heat index.
     *
     * @param heatIndexF the heat index to evaluate
     * @return the rest minutes, or {@code 0} if no rest cadence is mandated
     */
    default int restMinutesPerHour(double heatIndexF) {
        return governingRestRule(heatIndexF).map(HeatRule::restMinutesPerHour).orElse(0);
    }
}
