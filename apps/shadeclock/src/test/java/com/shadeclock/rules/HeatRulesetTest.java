package com.shadeclock.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the jurisdiction rules engine.
 *
 * <p>Several assertions here check <b>honesty</b> rather than behaviour - that unverified
 * rulesets admit they are unverified, and that output built on them carries a warning. Those
 * are encoded as tests so that a future change which quietly drops the caveat fails the
 * build instead of shipping.
 */
class HeatRulesetTest {

    private final CaliforniaHeatRuleset california = new CaliforniaHeatRuleset();
    private final FederalBaselineRuleset baseline = new FederalBaselineRuleset();
    private final RulesetRegistry registry = new RulesetRegistry(List.of(california, baseline));

    @Test
    @DisplayName("no rules fire on a mild day")
    void noRulesOnMildDay() {
        assertThat(california.applicableRules(70)).isEmpty();
        assertThat(california.restMinutesPerHour(70)).isZero();
    }

    @Test
    @DisplayName("shade and water requirements start at 80°F")
    void shadeStartsAt80() {
        assertThat(california.applicableRules(80)).isNotEmpty();
        assertThat(california.applicableRules(80))
                .anySatisfy(rule -> assertThat(rule.requirement()).contains("shade"));
    }

    @Test
    @DisplayName("rest cadence increases as it gets hotter")
    void restIncreasesWithHeat() {
        int mild = california.restMinutesPerHour(85);
        int high = california.restMinutesPerHour(96);
        int danger = california.restMinutesPerHour(106);

        assertThat(mild).isZero();
        assertThat(high).isPositive();
        assertThat(danger).isGreaterThan(high);
    }

    @Test
    @DisplayName("the STRICTEST triggered rule governs, not the last one listed")
    void strictestRuleGoverns() {
        // At 106°F both the 95°F and 105°F rules are triggered. Taking whichever appeared
        // last in the list would be a subtle, dangerous bug if the list were ever reordered.
        assertThat(california.applicableRules(106)).hasSizeGreaterThan(1);
        assertThat(california.governingRestRule(106))
                .hasValueSatisfying(rule -> assertThat(rule.restMinutesPerHour()).isEqualTo(15));
    }

    @Test
    @DisplayName("an unknown jurisdiction falls back to guidance rather than to nothing")
    void unknownJurisdictionFallsBack() {
        // A crew in a state we have not modelled still deserves water-and-shade advice.
        HeatRuleset texas = registry.forJurisdiction("TX");

        assertThat(texas.jurisdictionCode()).isEqualTo(RulesetRegistry.FALLBACK_CODE);
        assertThat(registry.hasSpecificRuleset("TX")).isFalse();
        assertThat(texas.applicableRules(95)).isNotEmpty();
    }

    @Test
    @DisplayName("a null or blank jurisdiction also falls back")
    void nullJurisdictionFallsBack() {
        assertThat(registry.forJurisdiction(null).jurisdictionCode())
                .isEqualTo(RulesetRegistry.FALLBACK_CODE);
        assertThat(registry.forJurisdiction("  ").jurisdictionCode())
                .isEqualTo(RulesetRegistry.FALLBACK_CODE);
    }

    @Test
    @DisplayName("jurisdiction lookup is case-insensitive")
    void lookupIsCaseInsensitive() {
        assertThat(registry.forJurisdiction("ca").jurisdictionCode()).isEqualTo("CA");
        assertThat(registry.forJurisdiction("Ca").jurisdictionCode()).isEqualTo("CA");
    }

    @Test
    @DisplayName("a registry with no fallback fails at startup, not at 2pm on a hot day")
    void missingFallbackFailsFast() {
        assertThatThrownBy(() -> new RulesetRegistry(List.of(california)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RulesetRegistry.FALLBACK_CODE);
    }

    @Test
    @DisplayName("every ruleset declares its provenance and carries a citation")
    void everyRulesetIsHonestAboutItsSource() {
        for (HeatRuleset ruleset : List.of(california, baseline)) {
            assertThat(ruleset.citation()).isNotBlank();
            assertThat(ruleset.verificationStatus()).isNotNull();
            assertThat(ruleset.rules()).allSatisfy(rule ->
                    assertThat(rule.citation()).isNotBlank());
        }
    }

    @Test
    @DisplayName("v0.1 rulesets admit they are not verified against regulation text")
    void v01RulesetsAdmitTheyAreUnverified() {
        // If someone verifies California against 8 CCR §3395 and flips the status, this
        // test should be updated deliberately - not silently.
        assertThat(california.verificationStatus())
                .isEqualTo(VerificationStatus.UNVERIFIED_SECONDARY_SOURCE);
        assertThat(california.verificationStatus().requiresWarning()).isTrue();

        assertThat(baseline.verificationStatus()).isEqualTo(VerificationStatus.GUIDANCE_NOT_LAW);
        assertThat(baseline.verificationStatus().requiresWarning()).isTrue();
    }

    @Test
    @DisplayName("the federal baseline does not claim to be law")
    void baselineDoesNotClaimToBeLaw() {
        assertThat(baseline.citation()).contains("NOT legal requirements");
        assertThat(baseline.verificationStatus().caveat()).contains("not legal requirements");
    }

    @Test
    @DisplayName("an impossible rest cadence is rejected at construction")
    void rejectsImpossibleRestCadence() {
        assertThatThrownBy(() -> new HeatRule(95, "Rest forever", 90, "test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HeatRule(95, "Negative rest", -5, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
