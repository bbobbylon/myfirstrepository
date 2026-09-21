package com.refillradar.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.refillradar.domain.ShortageRecord;
import com.refillradar.domain.ShortageStatus;

/**
 * Tests for the last-known-good cache.
 *
 * <p>The behaviour under test is a safety property, not a performance one: an outage must
 * never be able to present as "no shortages".
 */
class ShortageCacheTest {

    private static final Instant NOW = Instant.parse("2026-09-21T09:00:00Z");

    private ShortageCache cacheAt(Instant instant) {
        return new ShortageCache(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private List<ShortageRecord> oneRecord() {
        return List.of(new ShortageRecord("LEVETIRACETAM", "Keppra", "Co",
                ShortageStatus.CURRENT, "Limited", "Manufacturing delay",
                List.of(), "TABLET", List.of(), null, null));
    }

    @Test
    @DisplayName("an empty cache reports that it has never fetched, and says so plainly")
    void emptyCacheAdmitsItHasNothing() {
        ShortageCache cache = cacheAt(NOW);

        assertThat(cache.current()).isEmpty();
        assertThat(cache.isStale()).isTrue();
        // The critical sentence: a user must never read an empty result as reassurance.
        assertThat(cache.freshnessNote()).contains("Do NOT read an empty result as an all-clear");
    }

    @Test
    @DisplayName("a fresh fetch is stored and reported as fresh")
    void freshFetchIsStored() {
        ShortageCache cache = cacheAt(NOW);
        cache.store(oneRecord(), "test source");

        assertThat(cache.current()).isPresent();
        assertThat(cache.current().orElseThrow().records()).hasSize(1);
        assertThat(cache.isStale()).isFalse();
        assertThat(cache.freshnessNote()).contains("last updated");
    }

    @Test
    @DisplayName("data older than the staleness window is flagged, with its age")
    void oldDataIsFlaggedStale() {
        // Stored 40 hours before "now", past the 36-hour window.
        ShortageCache cache = new ShortageCache(new SteppingClock(
                NOW.minus(Duration.ofHours(40)), NOW));
        cache.store(oneRecord(), "test source");

        assertThat(cache.isStale()).isTrue();
        assertThat(cache.freshnessNote())
                .contains("40 hours old")
                .contains("check with your pharmacist");
    }

    @Test
    @DisplayName("the staleness boundary is exact")
    void stalenessBoundaryIsExact() {
        ShortageCache justInside = new ShortageCache(new SteppingClock(
                NOW.minus(ShortageCache.STALE_AFTER), NOW));
        justInside.store(oneRecord(), "s");
        assertThat(justInside.isStale()).isFalse();

        ShortageCache justOutside = new ShortageCache(new SteppingClock(
                NOW.minus(ShortageCache.STALE_AFTER).minusSeconds(1), NOW));
        justOutside.store(oneRecord(), "s");
        assertThat(justOutside.isStale()).isTrue();
    }

    @Test
    @DisplayName("stored records cannot be mutated through the returned snapshot")
    void snapshotIsImmutable() {
        ShortageCache cache = cacheAt(NOW);
        cache.store(oneRecord(), "test source");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> cache.current().orElseThrow().records().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** A clock that returns one instant for the first read and another thereafter. */
    private static final class SteppingClock extends Clock {
        private final Instant first;
        private final Instant rest;
        private boolean used;

        SteppingClock(Instant first, Instant rest) {
            this.first = first;
            this.rest = rest;
        }

        @Override
        public Instant instant() {
            if (!used) {
                used = true;
                return first;
            }
            return rest;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
