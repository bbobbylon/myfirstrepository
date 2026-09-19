package com.refillradar.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.refillradar.domain.Medication;
import com.refillradar.domain.ShortageMatch;
import com.refillradar.domain.ShortageRecord;
import com.refillradar.domain.ShortageStatus;
import com.refillradar.domain.SupplyRisk;

/**
 * Tests for {@link AlertComposer}.
 *
 * <p>Unusually for a test class, several of these assert on <em>wording</em>. That is
 * deliberate: the boundary between "information tool" and "medical advice" is drawn by the
 * sentences this class emits, not by a disclaimer elsewhere. Encoding those rules as tests
 * means a future change that crosses the line fails the build instead of shipping quietly.
 */
class AlertComposerTest {

    private final AlertComposer composer = new AlertComposer();

    private ShortageMatch match(String drug, String generic, long daysRemaining,
                                SupplyRisk risk, ShortageStatus status) {
        Medication medication = new Medication("m1", "u1", drug, generic,
                LocalDate.of(2026, 9, 1), 30);
        ShortageRecord shortage = new ShortageRecord(generic, drug, "Test Co", status,
                "Limited supply", "Manufacturing delay", List.of("Test"), "TABLET",
                List.of("10 mg"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1));

        return new ShortageMatch(medication, shortage, LocalDate.of(2026, 10, 1),
                daysRemaining, risk, generic);
    }

    @Test
    @DisplayName("subject names the drug when there is exactly one match")
    void singleMatchSubject() {
        String subject = composer.composeSubject(
                List.of(match("Keppra", "levetiracetam", 5, SupplyRisk.CRITICAL,
                        ShortageStatus.CURRENT)));

        assertThat(subject).contains("Keppra");
    }

    @Test
    @DisplayName("subject counts them when there are several")
    void multipleMatchSubject() {
        String subject = composer.composeSubject(List.of(
                match("Keppra", "levetiracetam", 5, SupplyRisk.CRITICAL, ShortageStatus.CURRENT),
                match("Ozempic", "semaglutide", 20, SupplyRisk.MEDIUM, ShortageStatus.CURRENT)));

        assertThat(subject).contains("2");
    }

    @Test
    @DisplayName("body never suggests an alternative medicine")
    void neverSuggestsAlternatives() {
        String body = composer.composeBody(
                List.of(match("Keppra", "levetiracetam", 5, SupplyRisk.CRITICAL,
                        ShortageStatus.CURRENT)),
                "US FDA drug shortage database");

        // Suggesting a substitute is a clinical decision and would change what this
        // software legally is. Asserted rather than trusted to code review.
        assertThat(body.toLowerCase())
                .doesNotContain("try instead", "switch to", "we recommend taking",
                        "alternative medication is", "you should take");
    }

    @Test
    @DisplayName("body states the limits of what a shortage listing means")
    void explainsWhatItDoesNotMean() {
        String body = composer.composeBody(
                List.of(match("Keppra", "levetiracetam", 5, SupplyRisk.CRITICAL,
                        ShortageStatus.CURRENT)),
                "US FDA drug shortage database");

        assertThat(body).contains("does NOT mean your pharmacy is out of stock");
        assertThat(body).contains("prescriber or pharmacist");
    }

    @Test
    @DisplayName("body always cites its source")
    void citesSource() {
        String body = composer.composeBody(
                List.of(match("Keppra", "levetiracetam", 5, SupplyRisk.CRITICAL,
                        ShortageStatus.CURRENT)),
                "US FDA drug shortage database (openFDA)");

        assertThat(body).contains("Source: US FDA drug shortage database (openFDA)");
    }

    @Test
    @DisplayName("body hands the patient a ready-made question to ask")
    void providesAQuestionToAsk() {
        String body = composer.composeBody(
                List.of(match("Keppra", "levetiracetam", 5, SupplyRisk.CRITICAL,
                        ShortageStatus.CURRENT)),
                "US FDA drug shortage database");

        // The product goal: give the patient a well-framed question, not an answer.
        assertThat(body).contains("A question you can ask");
    }

    @Test
    @DisplayName("an uncertain FDA status is surfaced to the reader, not hidden")
    void surfacesUncertainty() {
        String body = composer.composeBody(
                List.of(match("Omnipaque", "iohexol", 5, SupplyRisk.CRITICAL,
                        ShortageStatus.UNKNOWN)),
                "US FDA drug shortage database");

        assertThat(body).contains("could not fully interpret");
    }

    @Test
    @DisplayName("a passed run-out date invites a correction rather than raising alarm")
    void staleDataInvitesCorrection() {
        String body = composer.composeBody(
                List.of(match("Keppra", "levetiracetam", -10, SupplyRisk.WATCH,
                        ShortageStatus.CURRENT)),
                "US FDA drug shortage database");

        assertThat(body).contains("have you refilled since?");
    }

    @Test
    @DisplayName("refuses to compose an alert with nothing to say")
    void refusesEmptyInput() {
        assertThatThrownBy(() -> composer.composeSubject(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> composer.composeBody(null, "src"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
