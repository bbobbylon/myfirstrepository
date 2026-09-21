package com.safeword.scam;

import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Turns a set of reported tactics into a recommendation.
 *
 * <p>Pure and dependency-light, so the whole scoring model is exhaustively testable: the
 * output is shown to a frightened person during a live call and must behave identically
 * every time.
 */
@Service
public class RiskScorer {

    private final ScamPatternLibrary library;

    /**
     * @param library supplies pattern matching for the assessment
     */
    public RiskScorer(ScamPatternLibrary library) {
        this.library = library;
    }

    /**
     * Assesses a call from the tactics the user reported.
     *
     * @param observed the tactics ticked; {@code null} is treated as none
     * @return the assessment, never {@code null}
     */
    public RiskAssessment assess(Set<PressureSignal> observed) {
        Set<PressureSignal> signals = observed == null ? Set.of() : observed;

        int score = signals.stream().mapToInt(PressureSignal::weight).sum();
        RiskLevel level = RiskLevel.fromScore(score);

        return new RiskAssessment(
                score, level, signals, library.matching(signals), level.headline());
    }

    /**
     * Whether any single reported tactic is severe enough to warrant stopping on its own.
     *
     * <p>Some tactics do not need corroboration. A request for gift cards, or for remote
     * access to a computer, is on its own sufficient reason to end a call - there is no
     * legitimate context in which either arrives unprompted by phone.
     *
     * @param observed the tactics ticked
     * @return {@code true} if a single-signal stop applies
     */
    public boolean hasStandaloneRedFlag(Set<PressureSignal> observed) {
        if (observed == null) {
            return false;
        }
        return observed.contains(PressureSignal.IRREVERSIBLE_PAYMENT)
                || observed.contains(PressureSignal.REMOTE_ACCESS);
    }
}
