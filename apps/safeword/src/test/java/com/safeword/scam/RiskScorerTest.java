package com.safeword.scam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for the tactic-based risk model and the scam pattern library. */
class RiskScorerTest {

    private final ScamPatternLibrary library = new ScamPatternLibrary();
    private final RiskScorer scorer = new RiskScorer(library);

    @Test
    @DisplayName("no reported tactics means no flags")
    void noSignalsMeansNoFlags() {
        assertThat(scorer.assess(Set.of()).level()).isEqualTo(RiskLevel.NO_FLAGS);
        assertThat(scorer.assess(null).level()).isEqualTo(RiskLevel.NO_FLAGS);
    }

    @Test
    @DisplayName("the classic cloned-voice grandparent scam scores STOP")
    void grandparentScamScoresStop() {
        // Family emergency + secrecy + urgency + gift cards. The signature has not changed;
        // only the quality of the voice has.
        RiskAssessment assessment = scorer.assess(Set.of(
                PressureSignal.FAMILY_EMERGENCY,
                PressureSignal.SECRECY,
                PressureSignal.URGENCY,
                PressureSignal.IRREVERSIBLE_PAYMENT));

        assertThat(assessment.level()).isEqualTo(RiskLevel.STOP);
        assertThat(assessment.likelyPatterns()).isNotEmpty();
        assertThat(assessment.likelyPatterns().getFirst().name()).contains("Family emergency");
    }

    @Test
    @DisplayName("the tech support pattern is recognised")
    void techSupportIsRecognised() {
        RiskAssessment assessment = scorer.assess(Set.of(
                PressureSignal.REMOTE_ACCESS,
                PressureSignal.AUTHORITY_CLAIM,
                PressureSignal.URGENCY));

        assertThat(assessment.level()).isEqualTo(RiskLevel.STOP);
        assertThat(assessment.likelyPatterns())
                .anySatisfy(p -> assertThat(p.name()).contains("Tech or customer support"));
    }

    @Test
    @DisplayName("gift cards alone is a standalone red flag")
    void giftCardsAloneIsARedFlag() {
        // No legitimate context delivers this request unprompted by phone, so it needs no
        // corroboration from other tactics.
        assertThat(scorer.hasStandaloneRedFlag(Set.of(PressureSignal.IRREVERSIBLE_PAYMENT)))
                .isTrue();
        assertThat(scorer.hasStandaloneRedFlag(Set.of(PressureSignal.REMOTE_ACCESS))).isTrue();
        assertThat(scorer.hasStandaloneRedFlag(Set.of(PressureSignal.INBOUND_CONTACT)))
                .isFalse();
        assertThat(scorer.hasStandaloneRedFlag(null)).isFalse();
    }

    @Test
    @DisplayName("scores rise monotonically as more tactics are reported")
    void scoresRiseWithMoreSignals() {
        int one = scorer.assess(Set.of(PressureSignal.URGENCY)).score();
        int two = scorer.assess(Set.of(PressureSignal.URGENCY, PressureSignal.SECRECY)).score();
        int three = scorer.assess(Set.of(PressureSignal.URGENCY, PressureSignal.SECRECY,
                PressureSignal.IRREVERSIBLE_PAYMENT)).score();

        assertThat(one).isLessThan(two);
        assertThat(two).isLessThan(three);
    }

    @Test
    @DisplayName("risk thresholds are exact")
    void thresholdsAreExact() {
        assertThat(RiskLevel.fromScore(0)).isEqualTo(RiskLevel.NO_FLAGS);
        assertThat(RiskLevel.fromScore(1)).isEqualTo(RiskLevel.SLOW_DOWN);
        assertThat(RiskLevel.fromScore(4)).isEqualTo(RiskLevel.SLOW_DOWN);
        assertThat(RiskLevel.fromScore(5)).isEqualTo(RiskLevel.VERIFY);
        assertThat(RiskLevel.fromScore(8)).isEqualTo(RiskLevel.VERIFY);
        assertThat(RiskLevel.fromScore(9)).isEqualTo(RiskLevel.STOP);
    }

    @ParameterizedTest
    @EnumSource(RiskLevel.class)
    @DisplayName("every level routes to a safe action that costs nothing if the call is real")
    void everyLevelRoutesSomewhereSafe(RiskLevel level) {
        // Hanging up and calling back, or asking for the passphrase, cost nothing when the
        // call is genuine - which is what makes them safe to recommend even when we are wrong.
        assertThat(level.action()).isNotBlank();
        assertThat(level.headline()).isNotBlank();
    }

    @Test
    @DisplayName("assessments never claim certainty")
    void assessmentsNeverClaimCertainty() {
        // Overstating confidence would earn exactly the misplaced trust these scams exploit.
        RiskAssessment assessment = scorer.assess(Set.of(PressureSignal.IRREVERSIBLE_PAYMENT));

        assertThat(assessment.confidenceCaveat())
                .contains("not a certainty")
                .contains("costs you nothing");
    }

    @Test
    @DisplayName("weak evidence produces weak matches, not a confident verdict")
    void weakEvidenceDoesNotProduceConfidentMatches() {
        // A single generic signal should not conjure a named scam pattern out of nothing.
        assertThat(library.matching(Set.of(PressureSignal.INBOUND_CONTACT))).isEmpty();
    }

    @Test
    @DisplayName("every pattern names its tells, its remedy, and its reported scale")
    void everyPatternIsComplete() {
        assertThat(library.all()).isNotEmpty().allSatisfy(pattern -> {
            assertThat(pattern.name()).isNotBlank();
            assertThat(pattern.howItWorks()).isNotBlank();
            assertThat(pattern.signals()).isNotEmpty();
            assertThat(pattern.whatToDo()).isNotEmpty();
            assertThat(pattern.reportedLosses()).isNotBlank();
        });
    }

    @Test
    @DisplayName("the library never tells people to detect a fake voice themselves")
    void libraryNeverAsksPeopleToJudgeVoices() {
        // The whole thesis: a human cannot answer "does this voice sound real?" any more,
        // so no advice in this app may ask them to.
        String allAdvice = library.all().stream()
                .flatMap(p -> p.whatToDo().stream())
                .reduce("", (a, b) -> a + " " + b).toLowerCase();

        assertThat(allAdvice)
                .doesNotContain("listen carefully to the voice")
                .doesNotContain("if the voice sounds")
                .doesNotContain("detect");
    }

    @Test
    @DisplayName("the family emergency pattern points at the passphrase, not at voice analysis")
    void familyEmergencyPointsAtThePassphrase() {
        ScamPattern pattern = library.all().stream()
                .filter(p -> p.name().contains("Family emergency")).findFirst().orElseThrow();

        assertThat(pattern.whatToDo().getFirst()).contains("PASSPHRASE");
        assertThat(pattern.howItWorks()).contains("clone it");
    }

    @Test
    @DisplayName("patterns are returned best-match-first")
    void patternsAreOrderedByMatchStrength() {
        Set<PressureSignal> signals = Set.of(
                PressureSignal.FAMILY_EMERGENCY, PressureSignal.SECRECY,
                PressureSignal.URGENCY, PressureSignal.IRREVERSIBLE_PAYMENT);

        var matches = library.matching(signals);
        assertThat(matches).hasSizeGreaterThan(1);
        assertThat(matches.getFirst().matchCount(signals))
                .isGreaterThanOrEqualTo(matches.get(1).matchCount(signals));
    }
}
