package com.refillradar.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.refillradar.domain.Medication;
import com.refillradar.domain.ShortageMatch;
import com.refillradar.domain.ShortageRecord;
import com.refillradar.domain.ShortageStatus;
import com.refillradar.domain.SupplyRisk;
import com.refillradar.refill.RefillProjector;

/**
 * Tests for {@link ShortageMatcher} - the engine that decides whether a user hears from us.
 *
 * <p>Runs entirely in memory: no database, no network, no Docker. The whole suite executes
 * in milliseconds, which is what makes it something you actually run on every change rather
 * than something you skip because it is slow.
 */
class ShortageMatcherTest {

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneOffset.UTC);

    private final ShortageMatcher matcher =
            new ShortageMatcher(new DrugNameNormalizer(), new RefillProjector(fixedClock));

    private ShortageRecord shortage(String generic, String brand, ShortageStatus status) {
        return new ShortageRecord(generic, brand, "Test Co", status, "Limited",
                "Manufacturing delay", List.of("Test"), "TABLET", List.of("10 mg"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1));
    }

    private Medication medication(String displayName, String searchTerm, int daysSupply) {
        return new Medication("med-1", "user-1", displayName, searchTerm,
                LocalDate.of(2026, 9, 1), daysSupply);
    }

    @Test
    @DisplayName("matches a user's brand name against the FDA's generic name")
    void matchesBrandAgainstGeneric() {
        List<Medication> meds = List.of(medication("Adderall XR 10mg", "Adderall", 30));
        List<ShortageRecord> shortages = List.of(
                shortage("AMPHETAMINE ASPARTATE; AMPHETAMINE SULFATE", null, ShortageStatus.CURRENT));

        List<ShortageMatch> matches = matcher.findMatches(meds, shortages);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().matchedOn()).isEqualTo("amphetamine");
        assertThat(matches.getFirst().medication().displayName()).isEqualTo("Adderall XR 10mg");
    }

    @Test
    @DisplayName("ignores shortages the FDA has marked resolved")
    void ignoresResolvedShortages() {
        List<Medication> meds = List.of(medication("Amoxicillin 500mg", "amoxicillin", 14));
        List<ShortageRecord> shortages =
                List.of(shortage("AMOXICILLIN", "Amoxil", ShortageStatus.RESOLVED));

        assertThat(matcher.findMatches(meds, shortages)).isEmpty();
    }

    @Test
    @DisplayName("an unrecognised FDA status still produces a match, flagged as uncertain")
    void unknownStatusStillWarns() {
        List<Medication> meds = List.of(medication("Omnipaque", "iohexol", 20));
        List<ShortageRecord> shortages =
                List.of(shortage("IOHEXOL", "Omnipaque", ShortageStatus.UNKNOWN));

        List<ShortageMatch> matches = matcher.findMatches(meds, shortages);

        // Failing safe: we would rather ask an unnecessary question than create false
        // confidence. The uncertainty is carried through so the alert can show it.
        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().hasUncertainStatus()).isTrue();
    }

    @Test
    @DisplayName("does not match different drugs with similar names")
    void noFalsePositiveOnSimilarNames() {
        List<Medication> meds = List.of(medication("Methamphetamine", "methamphetamine", 30));
        List<ShortageRecord> shortages =
                List.of(shortage("AMPHETAMINE SULFATE", null, ShortageStatus.CURRENT));

        assertThat(matcher.findMatches(meds, shortages)).isEmpty();
    }

    @Test
    @DisplayName("orders results by soonest run-out, not by FDA listing order")
    void ordersByUrgency() {
        Medication urgent = new Medication("m1", "user-1", "Keppra", "levetiracetam",
                LocalDate.of(2026, 9, 1), 20);                       // runs out 21 Sept
        Medication distant = new Medication("m2", "user-1", "Ozempic", "semaglutide",
                LocalDate.of(2026, 9, 1), 60);                       // runs out 31 Oct

        List<ShortageRecord> shortages = List.of(
                shortage("SEMAGLUTIDE", "Ozempic", ShortageStatus.CURRENT),
                shortage("LEVETIRACETAM", "Keppra", ShortageStatus.CURRENT));

        List<ShortageMatch> matches = matcher.findMatches(List.of(distant, urgent), shortages);

        assertThat(matches).hasSize(2);
        assertThat(matches.getFirst().medication().displayName()).isEqualTo("Keppra");
        assertThat(matches.getFirst().daysRemaining())
                .isLessThan(matches.get(1).daysRemaining());
    }

    @Test
    @DisplayName("alertable matches exclude low-urgency ones")
    void filtersToAlertable() {
        Medication plenty = new Medication("m1", "user-1", "Ozempic", "semaglutide",
                LocalDate.of(2026, 9, 1), 120);                      // ~72 days left -> WATCH
        List<ShortageRecord> shortages =
                List.of(shortage("SEMAGLUTIDE", "Ozempic", ShortageStatus.CURRENT));

        assertThat(matcher.findMatches(List.of(plenty), shortages)).hasSize(1);
        assertThat(matcher.findAlertableMatches(List.of(plenty), shortages)).isEmpty();
    }

    @Test
    @DisplayName("matches either component of a combination product")
    void matchesCombinationComponent() {
        List<Medication> meds = List.of(medication("Amlodipine 10mg", "amlodipine", 25));
        List<ShortageRecord> shortages = List.of(shortage(
                "AMLODIPINE BESYLATE; BENAZEPRIL HYDROCHLORIDE", "Lotrel", ShortageStatus.CURRENT));

        List<ShortageMatch> matches = matcher.findMatches(meds, shortages);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().matchedOn()).isEqualTo("amlodipine");
    }

    @Test
    @DisplayName("computes urgency from the user's calendar, not the FDA's severity wording")
    void urgencyComesFromPatientTimeline() {
        Medication almostOut = new Medication("m1", "user-1", "Keppra", "levetiracetam",
                LocalDate.of(2026, 9, 1), 24);                       // runs out 25 Sept -> 6 days
        List<ShortageRecord> shortages =
                List.of(shortage("LEVETIRACETAM", "Keppra", ShortageStatus.CURRENT));

        List<ShortageMatch> matches = matcher.findMatches(List.of(almostOut), shortages);

        assertThat(matches.getFirst().risk()).isEqualTo(SupplyRisk.CRITICAL);
        assertThat(matches.getFirst().warrantsAlert()).isTrue();
    }

    @Test
    @DisplayName("empty and null inputs yield no matches rather than errors")
    void handlesEmptyInput() {
        assertThat(matcher.findMatches(null, null)).isEmpty();
        assertThat(matcher.findMatches(List.of(), List.of())).isEmpty();
        assertThat(matcher.findMatches(
                List.of(medication("Keppra", "levetiracetam", 30)), List.of())).isEmpty();
    }
}
