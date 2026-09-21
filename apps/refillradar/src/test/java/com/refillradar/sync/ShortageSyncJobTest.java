package com.refillradar.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.refillradar.alert.AlertComposer;
import com.refillradar.alert.AlertDispatcher;
import com.refillradar.alert.AlertLedger;
import com.refillradar.alert.AlertService;
import com.refillradar.domain.Medication;
import com.refillradar.domain.ShortageRecord;
import com.refillradar.domain.ShortageStatus;
import com.refillradar.matching.DrugNameNormalizer;
import com.refillradar.matching.ShortageMatcher;
import com.refillradar.refill.RefillProjector;
import com.refillradar.shortage.ShortageFetchException;
import com.refillradar.shortage.ShortageSource;
import com.refillradar.store.ContactRepository;
import com.refillradar.store.InMemoryMedicationRepository;

/**
 * Tests for the nightly sync - the job that finally closes RefillRadar's core loop.
 *
 * <p>The single most important test here is {@link #failedFetchIsNeverAnAllClear}. Everything
 * else is plumbing; that one is the product's central promise.
 */
class ShortageSyncJobTest {

    private static final Instant NOW = Instant.parse("2026-09-21T03:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryMedicationRepository medications;
    private ContactRepository contacts;
    private ShortageCache cache;
    private AlertLedger ledger;
    private RecordingDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        medications = new InMemoryMedicationRepository();
        contacts = new ContactRepository();
        cache = new ShortageCache(FIXED);
        ledger = new AlertLedger(FIXED);
        dispatcher = new RecordingDispatcher();
    }

    private ShortageSyncJob jobWith(ShortageSource source) {
        // Clock fixed to a day when the user has ~10 days of supply left - CRITICAL, and
        // therefore alertable.
        RefillProjector projector = new RefillProjector(FIXED);
        ShortageMatcher matcher = new ShortageMatcher(new DrugNameNormalizer(), projector);
        AlertService alerts = new AlertService(medications, contacts, matcher,
                new AlertComposer(), ledger, dispatcher);
        return new ShortageSyncJob(source, cache, alerts, FIXED);
    }

    private void givenUserTakingKeppra(String userId, boolean reachable) {
        medications.save(new Medication("m-" + userId, userId, "Keppra 500mg",
                "levetiracetam", LocalDate.of(2026, 9, 1), 25));
        if (reachable) {
            contacts.setEmail(userId, userId + "@example.invalid");
        }
    }

    private List<ShortageRecord> keppraShortage() {
        return List.of(new ShortageRecord("LEVETIRACETAM", "Keppra", "Sample Co",
                ShortageStatus.CURRENT, "Limited", "Shortage of an active ingredient",
                List.of("CNS"), "TABLET", List.of("500 mg"), null, null));
    }

    @Test
    @DisplayName("THE critical test: a failed fetch is never an all-clear")
    void failedFetchIsNeverAnAllClear() {
        // Seed the cache with a good fetch, then make the next fetch fail.
        givenUserTakingKeppra("robert", true);
        jobWith(new StubSource(keppraShortage())).runSync();
        dispatcher.sent.clear();

        SyncOutcome outcome = jobWith(new FailingSource()).runSync();

        assertThat(outcome.succeeded()).isFalse();
        // The previous feed survives. Day-old data clearly labelled is vastly better than
        // an empty result that reads as reassurance.
        assertThat(cache.current()).isPresent();
        assertThat(cache.current().orElseThrow().records()).hasSize(1);
        // And crucially, no alerting pass ran over empty data.
        assertThat(dispatcher.sent).isEmpty();
        assertThat(outcome.note()).contains("This is NOT an all-clear");
    }

    @Test
    @DisplayName("a successful sync caches the feed and alerts the affected user")
    void successfulSyncAlerts() {
        givenUserTakingKeppra("robert", true);

        SyncOutcome outcome = jobWith(new StubSource(keppraShortage())).runSync();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(outcome.recordsFetched()).isEqualTo(1);
        assertThat(outcome.alertsSent()).isEqualTo(1);
        assertThat(cache.current()).isPresent();

        assertThat(dispatcher.sent).hasSize(1);
        assertThat(dispatcher.sent.getFirst().body()).contains("Keppra 500mg");
    }

    @Test
    @DisplayName("running twice does NOT alert twice")
    void runningTwiceDoesNotAlertTwice() {
        // Without this, a nightly job emails the same person every night for a quarter.
        givenUserTakingKeppra("robert", true);

        SyncOutcome first = jobWith(new StubSource(keppraShortage())).runSync();
        SyncOutcome second = jobWith(new StubSource(keppraShortage())).runSync();

        assertThat(first.alertsSent()).isEqualTo(1);
        assertThat(second.alertsSent()).isZero();
        assertThat(second.alertsSuppressed()).isEqualTo(1);
        assertThat(dispatcher.sent).hasSize(1);
    }

    @Test
    @DisplayName("a user with no matching medication gets nothing")
    void unaffectedUserIsNotAlerted() {
        medications.save(new Medication("m1", "sam", "Ibuprofen", "ibuprofen",
                LocalDate.of(2026, 9, 1), 25));
        contacts.setEmail("sam", "sam@example.invalid");

        SyncOutcome outcome = jobWith(new StubSource(keppraShortage())).runSync();

        assertThat(outcome.alertsSent()).isZero();
        assertThat(dispatcher.sent).isEmpty();
    }

    @Test
    @DisplayName("a user who needs an alert but cannot be reached is reported, not swallowed")
    void unreachableUserIsReported() {
        // A silent failure of the product's core promise. Somebody should see it.
        givenUserTakingKeppra("unreachable", false);

        SyncOutcome outcome = jobWith(new StubSource(keppraShortage())).runSync();

        assertThat(outcome.alertsSent()).isZero();
        assertThat(outcome.note()).contains("no contact route");
    }

    @Test
    @DisplayName("an empty feed is still a success, and simply alerts nobody")
    void emptyFeedIsASuccessNotAFailure() {
        // Distinct from a FAILED fetch. "The FDA says nothing is short" and "we could not
        // reach the FDA" must never be conflated - that distinction is the whole point.
        givenUserTakingKeppra("robert", true);

        SyncOutcome outcome = jobWith(new StubSource(List.of())).runSync();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(outcome.alertsSent()).isZero();
        assertThat(cache.current()).isPresent();
        assertThat(outcome.note()).doesNotContain("NOT an all-clear");
    }

    @Test
    @DisplayName("the default dispatcher reports that it did NOT deliver")
    void loggingDispatcherAdmitsNonDelivery() {
        // Claiming delivery when a message only reached a log file is the same class of lie
        // as reporting "no shortages" during an outage.
        var logging = new com.refillradar.alert.LoggingAlertDispatcher();

        assertThat(logging.dispatch("u", "s", "b").delivered()).isFalse();
        assertThat(logging.describeChannel()).contains("NOT DELIVERED");
    }

    /**
     * Returns a fixed list. Written as a class rather than a lambda because
     * {@code ShortageSource} declares two methods and so is not a functional interface -
     * the provenance label is part of its contract, precisely so no source can be used
     * without saying where its data came from.
     */
    private record StubSource(List<ShortageRecord> records) implements ShortageSource {
        @Override
        public List<ShortageRecord> fetchCurrentShortages() {
            return records;
        }

        @Override
        public String describeSource() {
            return "stub test source";
        }
    }

    /** A source that always fails, to exercise the outage path. */
    private static final class FailingSource implements ShortageSource {
        @Override
        public List<ShortageRecord> fetchCurrentShortages() {
            throw new ShortageFetchException("openFDA unreachable");
        }

        @Override
        public String describeSource() {
            return "failing test source";
        }
    }

    /** Captures dispatches instead of sending them. */
    private static final class RecordingDispatcher implements AlertDispatcher {
        private final List<Sent> sent = new ArrayList<>();

        @Override
        public DispatchResult dispatch(String userId, String subject, String body) {
            sent.add(new Sent(userId, subject, body));
            return new DispatchResult(true, "recorded");
        }

        @Override
        public String describeChannel() {
            return "recording test dispatcher";
        }

        private record Sent(String userId, String subject, String body) {
        }
    }
}
