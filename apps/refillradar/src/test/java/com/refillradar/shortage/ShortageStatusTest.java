package com.refillradar.shortage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.refillradar.domain.ShortageStatus;

/**
 * Tests for {@link ShortageStatus}, especially the deliberate fail-safe behaviour.
 *
 * <p>The exact vocabulary the FDA uses in its {@code status} field could not be verified
 * against the live API from the build environment, so these tests assert the <em>mapping
 * rules</em> - including that anything unrecognised is treated as potentially active.
 */
class ShortageStatusTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "Current,                        CURRENT",
            "current,                        CURRENT",
            "Currently in Shortage,          CURRENT",
            "Active shortage,                CURRENT",
            "Resolved,                       RESOLVED",
            "No longer in shortage,          RESOLVED",
            "Discontinued,                   DISCONTINUED",
            "Discontinuation,                DISCONTINUED",
            "Under Review Pending Confirmation, UNKNOWN",
            "Something we have never seen,   UNKNOWN"
    })
    @DisplayName("maps FDA status text onto a closed set of states")
    void mapsStatusText(String raw, ShortageStatus expected) {
        assertThat(ShortageStatus.fromFdaStatus(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("'discontinued' wins over 'current' when a string contains both")
    void discontinuedTakesPrecedence() {
        // "Discontinued - no longer current" contains both keywords. Checking order
        // explicitly because a reordering of the if-chain would silently change meaning.
        assertThat(ShortageStatus.fromFdaStatus("Discontinued - no longer current"))
                .isEqualTo(ShortageStatus.DISCONTINUED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("absent status becomes UNKNOWN rather than throwing")
    void absentStatusIsUnknown(String raw) {
        assertThat(ShortageStatus.fromFdaStatus(raw)).isEqualTo(ShortageStatus.UNKNOWN);
    }

    @Test
    @DisplayName("only RESOLVED suppresses a warning - everything else warns")
    void onlyResolvedSuppressesWarning() {
        // The central safety decision of the application, asserted directly.
        assertThat(ShortageStatus.CURRENT.isPotentiallyActive()).isTrue();
        assertThat(ShortageStatus.DISCONTINUED.isPotentiallyActive()).isTrue();
        assertThat(ShortageStatus.UNKNOWN.isPotentiallyActive()).isTrue();
        assertThat(ShortageStatus.RESOLVED.isPotentiallyActive()).isFalse();
    }

    @Test
    @DisplayName("UNKNOWN is the only state flagged as not-confidently-understood")
    void onlyUnknownIsUnconfident() {
        assertThat(ShortageStatus.UNKNOWN.isConfident()).isFalse();
        assertThat(ShortageStatus.CURRENT.isConfident()).isTrue();
        assertThat(ShortageStatus.RESOLVED.isConfident()).isTrue();
        assertThat(ShortageStatus.DISCONTINUED.isConfident()).isTrue();
    }
}
