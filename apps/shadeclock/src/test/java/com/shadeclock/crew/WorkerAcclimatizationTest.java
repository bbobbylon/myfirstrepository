package com.shadeclock.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for heat acclimatisation tracking - the bookkeeping a foreman cannot do in their head.
 *
 * <p>Every assertion pins "today" to a fixed date. Without that, "a worker who started two
 * days ago is UNACCLIMATIZED" would be true today and false next week, and the suite would
 * decay into something people re-run until it goes green.
 */
class WorkerAcclimatizationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

    private Worker startedDaysAgo(int days) {
        return new Worker("w1", "Test Worker", TODAY.minusDays(days), null);
    }

    @ParameterizedTest(name = "{0} days in heat -> {1}")
    @CsvSource({
            " 0, UNACCLIMATIZED",
            " 1, UNACCLIMATIZED",
            " 2, UNACCLIMATIZED",
            " 3, ACCLIMATIZING",
            " 7, ACCLIMATIZING",
            "13, ACCLIMATIZING",
            "14, ACCLIMATIZED",
            "60, ACCLIMATIZED"
    })
    @DisplayName("adaptation builds over the first two weeks")
    void adaptationBuildsOverTime(int daysAgo, AcclimatizationStatus expected) {
        assertThat(startedDaysAgo(daysAgo).acclimatizationOn(TODAY)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a ten-year veteran back from two weeks off is high risk again")
    void returningVeteranLosesAdaptation() {
        // The scenario this whole class exists for. Everyone on site "knows" this person is
        // experienced, which is exactly why nobody watches them on their first day back.
        Worker veteran = new Worker("w2", "Veteran",
                TODAY.minusYears(10),        // started a decade ago
                TODAY.minusDays(1));         // came back yesterday

        assertThat(veteran.acclimatizationOn(TODAY))
                .isEqualTo(AcclimatizationStatus.LOST_ACCLIMATIZATION);
        assertThat(veteran.acclimatizationOn(TODAY).needsExtraPrecautions()).isTrue();
    }

    @Test
    @DisplayName("after a return, adaptation rebuilds on the same schedule")
    void adaptationRebuildsAfterReturn() {
        Worker returned = new Worker("w3", "Returned",
                TODAY.minusYears(3), TODAY.minusDays(5));

        // Five days back: past the highest-risk window but not yet fully re-adapted.
        assertThat(returned.acclimatizationOn(TODAY))
                .isEqualTo(AcclimatizationStatus.ACCLIMATIZING);

        Worker fullyBack = new Worker("w4", "Fully Back",
                TODAY.minusYears(3), TODAY.minusDays(20));
        assertThat(fullyBack.acclimatizationOn(TODAY))
                .isEqualTo(AcclimatizationStatus.ACCLIMATIZED);
    }

    @Test
    @DisplayName("an absence recorded before the start date is ignored")
    void staleAbsenceIsIgnored() {
        // Guards a plausible data-entry mistake: an absence that predates the current run
        // of work must not reset a clock that already started after it.
        Worker worker = new Worker("w5", "Worker",
                TODAY.minusDays(30), TODAY.minusDays(60));

        assertThat(worker.acclimatizationOn(TODAY)).isEqualTo(AcclimatizationStatus.ACCLIMATIZED);
    }

    @Test
    @DisplayName("a start date in the future is treated as unadapted, not superhuman")
    void futureStartDateFailsSafe() {
        // Bad data should fail towards caution. Counting negative days would otherwise
        // sail past the 14-day threshold check and report ACCLIMATIZED.
        Worker typo = new Worker("w6", "Typo", TODAY.plusDays(5), null);

        assertThat(typo.acclimatizationOn(TODAY)).isEqualTo(AcclimatizationStatus.UNACCLIMATIZED);
    }

    @Test
    @DisplayName("only a fully adapted worker escapes extra precautions")
    void onlyAcclimatizedIsClear() {
        assertThat(AcclimatizationStatus.ACCLIMATIZED.needsExtraPrecautions()).isFalse();
        assertThat(AcclimatizationStatus.UNACCLIMATIZED.needsExtraPrecautions()).isTrue();
        assertThat(AcclimatizationStatus.ACCLIMATIZING.needsExtraPrecautions()).isTrue();
        assertThat(AcclimatizationStatus.LOST_ACCLIMATIZATION.needsExtraPrecautions()).isTrue();
    }

    @Test
    @DisplayName("a worker without a start date is rejected at construction")
    void requiresStartDate() {
        assertThatThrownBy(() -> new Worker("w7", "No Date", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("heatWorkStartedOn");
    }

    @Test
    @DisplayName("a crew reports exactly which members need watching")
    void crewNamesWhoNeedsWatching() {
        Crew crew = new Crew("c1", "Paving Crew", "Route 12", 34.05, -118.24, "CA",
                java.util.List.of(
                        startedDaysAgo(30),                                   // adapted
                        new Worker("n1", "New Starter", TODAY.minusDays(1), null),
                        new Worker("n2", "Back Today", TODAY.minusYears(2), TODAY)));

        assertThat(crew.workersNeedingExtraPrecautions(TODAY))
                .extracting(Worker::name)
                .containsExactlyInAnyOrder("New Starter", "Back Today");
    }
}
