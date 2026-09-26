package com.refillradar.refill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.refillradar.domain.Medication;
import com.refillradar.domain.SupplyRisk;

/**
 * Tests for {@link RefillProjector}, demonstrating why the injected {@link Clock} matters.
 *
 * <p>Every test here freezes time at 19 September 2026. Without that, an assertion such as
 * "12 days remaining is HIGH risk" would be true today and false tomorrow, and the suite
 * would rot into something people re-run until it passes. Controlling time turns date
 * behaviour into something you can actually pin down.
 */
class RefillProjectorTest {

    /** A fixed "today" so every expectation below is stable forever. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC);

    private final RefillProjector projector = new RefillProjector(fixedClock);

    private Medication medicationFilledOn(LocalDate filled, int daysSupply) {
        return new Medication("id-1", "user-1", "Test Drug", "testdrug", filled, daysSupply);
    }

    @Test
    @DisplayName("the projector's idea of today comes from the injected clock")
    void todayComesFromClock() {
        assertThat(projector.today()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("run-out date is last fill date plus days of supply")
    void projectsRunOutDate() {
        Medication medication = medicationFilledOn(LocalDate.of(2026, 9, 1), 30);

        assertThat(medication.projectedRunOutDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(projector.daysRemaining(medication)).isEqualTo(12);
    }

    @ParameterizedTest(name = "{0} days of supply from 1 Sept -> {1}")
    @CsvSource({
            "25, CRITICAL",   // runs out 26 Sept -> 7 days away
            "26, HIGH",       // runs out 27 Sept -> 8 days away
            "32, HIGH",       // runs out 3 Oct   -> 14 days away (upper HIGH boundary)
            "40, MEDIUM",     // runs out 11 Oct  -> 22 days away
            "90, WATCH"       // runs out 30 Nov  -> 72 days away
    })
    @DisplayName("remaining supply maps onto the expected urgency level")
    void classifiesRisk(int daysSupply, SupplyRisk expected) {
        Medication medication = medicationFilledOn(LocalDate.of(2026, 9, 1), daysSupply);

        assertThat(projector.assessRisk(medication)).isEqualTo(expected);
    }

    @Test
    @DisplayName("boundaries are exact: 7 days is CRITICAL, 8 days is HIGH")
    void riskBoundariesAreExact() {
        // Off-by-one errors around thresholds are the most common bug in code like this,
        // so the boundaries are asserted directly rather than trusted.
        assertThat(SupplyRisk.fromDaysRemaining(7)).isEqualTo(SupplyRisk.CRITICAL);
        assertThat(SupplyRisk.fromDaysRemaining(8)).isEqualTo(SupplyRisk.HIGH);
        assertThat(SupplyRisk.fromDaysRemaining(14)).isEqualTo(SupplyRisk.HIGH);
        assertThat(SupplyRisk.fromDaysRemaining(15)).isEqualTo(SupplyRisk.MEDIUM);
        assertThat(SupplyRisk.fromDaysRemaining(30)).isEqualTo(SupplyRisk.MEDIUM);
        assertThat(SupplyRisk.fromDaysRemaining(31)).isEqualTo(SupplyRisk.WATCH);
    }

    @Test
    @DisplayName("an already-passed run-out date is WATCH, not CRITICAL")
    void alreadyPassedIsNotAnEmergency() {
        Medication stale = medicationFilledOn(LocalDate.of(2026, 8, 1), 10);

        assertThat(projector.daysRemaining(stale)).isNegative();
        // Almost always means the user refilled without telling us. Screaming CRITICAL at
        // someone whose data is merely stale is how an app teaches people to ignore it.
        assertThat(projector.assessRisk(stale)).isEqualTo(SupplyRisk.WATCH);
    }

    @Test
    @DisplayName("every risk level routes the patient to a human, never to a drug")
    void everyActionRoutesToAHuman() {
        // Guards the legal boundary: no recommended action may ever suggest a medicine.
        for (SupplyRisk risk : SupplyRisk.values()) {
            assertThat(risk.recommendedAction()).isNotBlank();
            assertThat(risk.recommendedAction().toLowerCase())
                    .doesNotContain("instead", "substitute", "switch to", "alternative drug");
        }
    }
}
