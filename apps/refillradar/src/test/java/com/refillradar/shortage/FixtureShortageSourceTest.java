package com.refillradar.shortage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.refillradar.domain.ShortageRecord;
import com.refillradar.domain.ShortageStatus;

/**
 * Tests that the bundled fixture parses into sane domain records.
 *
 * <p>This is the test that proves the offline development story actually works: the whole
 * data path from JSON to domain object runs with no network at all.
 */
class FixtureShortageSourceTest {

    private final FixtureShortageSource source = new FixtureShortageSource(new ObjectMapper());

    @Test
    @DisplayName("loads every record from the bundled fixture")
    void loadsFixture() {
        List<ShortageRecord> records = source.fetchCurrentShortages();

        assertThat(records).hasSize(8);
        assertThat(records).allSatisfy(record ->
                assertThat(record.genericName()).isNotBlank());
    }

    @Test
    @DisplayName("labels itself as non-live so a misconfiguration is visible")
    void describesItselfAsNotLive() {
        assertThat(source.describeSource()).contains("NOT LIVE");
    }

    @Test
    @DisplayName("parses FDA compact dates (yyyyMMdd)")
    void parsesDates() {
        ShortageRecord adderall = source.fetchCurrentShortages().stream()
                .filter(record -> "Adderall".equals(record.proprietaryName()))
                .findFirst()
                .orElseThrow();

        assertThat(adderall.initialPostingDate()).isEqualTo(LocalDate.of(2024, 10, 14));
        assertThat(adderall.updateDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("the fixture deliberately includes a resolved record and an unknown status")
    void fixtureCoversEdgeCases() {
        List<ShortageRecord> records = source.fetchCurrentShortages();

        // These awkward cases are the reason the fixture exists: a live feed will not
        // produce them to order, and they are where the logic is most likely to be wrong.
        assertThat(records).anySatisfy(record ->
                assertThat(record.status()).isEqualTo(ShortageStatus.RESOLVED));
        assertThat(records).anySatisfy(record ->
                assertThat(record.status()).isEqualTo(ShortageStatus.UNKNOWN));
        assertThat(records).anySatisfy(record ->
                assertThat(record.proprietaryName()).isNull());
    }

    @Test
    @DisplayName("a record with no brand name falls back to its generic name for display")
    void displayNameFallsBack() {
        ShortageRecord noBrand = source.fetchCurrentShortages().stream()
                .filter(record -> record.proprietaryName() == null)
                .findFirst()
                .orElseThrow();

        assertThat(noBrand.displayName()).isEqualTo(noBrand.genericName());
    }

    @Test
    @DisplayName("null list fields become empty lists, never null")
    void listFieldsAreNeverNull() {
        assertThat(source.fetchCurrentShortages()).allSatisfy(record -> {
            assertThat(record.therapeuticCategory()).isNotNull();
            assertThat(record.strengths()).isNotNull();
        });
    }

    @Test
    @DisplayName("unparseable dates degrade to null instead of aborting the load")
    void badDatesDoNotThrow() {
        assertThat(FdaDateParser.parseOrNull("not-a-date")).isNull();
        assertThat(FdaDateParser.parseOrNull(null)).isNull();
        assertThat(FdaDateParser.parseOrNull("")).isNull();
        assertThat(FdaDateParser.parseOrNull("20260115")).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(FdaDateParser.parseOrNull("2026-01-15")).isEqualTo(LocalDate.of(2026, 1, 15));
    }
}
