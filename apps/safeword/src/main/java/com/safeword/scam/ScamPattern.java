package com.safeword.scam;

import java.util.List;
import java.util.Set;

/**
 * A known scam shape, its tells, and what to do about it.
 *
 * @param name           short name a person would recognise
 * @param howItWorks     plain-language description of the setup
 * @param signals        the pressure tactics characteristic of this pattern
 * @param whatToDo       the concrete next step
 * @param reportedLosses what US reporting says this category cost, for context
 */
public record ScamPattern(
        String name,
        String howItWorks,
        Set<PressureSignal> signals,
        List<String> whatToDo,
        String reportedLosses) {

    /**
     * Defensive-copies the collections so a pattern cannot be mutated after publication.
     */
    public ScamPattern {
        signals = signals == null ? Set.of() : Set.copyOf(signals);
        whatToDo = whatToDo == null ? List.of() : List.copyOf(whatToDo);
    }

    /**
     * How many of this pattern's signals are present in a reported situation.
     *
     * @param observed the signals the caller reported
     * @return the count of matching signals
     */
    public long matchCount(Set<PressureSignal> observed) {
        if (observed == null) {
            return 0;
        }
        return signals.stream().filter(observed::contains).count();
    }
}
