package com.refillradar.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.refillradar.alert.AlertLedger;
import com.refillradar.alert.AlertRecordStore;
import com.refillradar.alert.InMemoryAlertRecordStore;
import com.refillradar.domain.Medication;
import com.refillradar.domain.ShortageMatch;
import com.refillradar.domain.ShortageRecord;
import com.refillradar.domain.ShortageStatus;
import com.refillradar.domain.SupplyRisk;

/**
 * Tests for alert de-duplication.
 *
 * <p>Two opposing failures are in tension here, and both are asserted: sending the same
 * message every night until people ignore it, and staying quiet when something genuinely
 * got worse.
 */
class AlertLedgerTest {

    private static final Instant DAY_ONE = Instant.parse("2026-09-01T03:00:00Z");

    private ShortageMatch match(SupplyRisk risk, String medicationId) {
        Medication medication = new Medication(medicationId, "user-1", "Keppra",
                "levetiracetam", LocalDate.of(2026, 9, 1), 30);
        ShortageRecord shortage = new ShortageRecord("LEVETIRACETAM", "Keppra", "Co",
                ShortageStatus.CURRENT, "Limited", "Delay", List.of(), "TABLET",
                List.of(), null, null);
        return new ShortageMatch(medication, shortage, LocalDate.of(2026, 10, 1),
                10, risk, "levetiracetam");
    }

    private AlertLedger ledgerAt(Instant instant) {
        return new AlertLedger(new InMemoryAlertRecordStore(),
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("a match never seen before is sent")
    void newMatchIsSent() {
        assertThat(ledgerAt(DAY_ONE).shouldSend(match(SupplyRisk.HIGH, "m1"))).isTrue();
    }

    @Test
    @DisplayName("the same unchanged match is NOT sent again the next night")
    void duplicateIsSuppressed() {
        // Shortages last months. Without this, a nightly job emails the same person about
        // the same shortage every night until they stop reading it - and an ignored alert
        // is worse than none, because it still implies someone is watching.
        AlertLedger ledger = ledgerAt(DAY_ONE);
        ShortageMatch first = match(SupplyRisk.HIGH, "m1");

        ledger.record(first);

        assertThat(ledger.shouldSend(match(SupplyRisk.HIGH, "m1"))).isFalse();
    }

    @Test
    @DisplayName("ESCALATION is sent, because it is new information")
    void escalationIsSent() {
        // The subtlety that separates a good ledger from a naive one. Going from HIGH to
        // CRITICAL means days left rather than weeks. Suppressing that would be the worst
        // possible behaviour.
        AlertLedger ledger = ledgerAt(DAY_ONE);
        ledger.record(match(SupplyRisk.HIGH, "m1"));

        assertThat(ledger.shouldSend(match(SupplyRisk.CRITICAL, "m1"))).isTrue();
    }

    @Test
    @DisplayName("de-escalation is NOT sent - it is not urgent news")
    void deEscalationIsSuppressed() {
        AlertLedger ledger = ledgerAt(DAY_ONE);
        ledger.record(match(SupplyRisk.CRITICAL, "m1"));

        assertThat(ledger.shouldSend(match(SupplyRisk.MEDIUM, "m1"))).isFalse();
    }

    @Test
    @DisplayName("after the repeat window, an unchanged match is sent again")
    void repeatsAfterTheWindow() {
        // A months-long shortage should produce an occasional reminder, not one message in
        // January and silence until March.
        //
        // Both ledgers SHARE one store, which is what makes this a real test of the window.
        // An earlier version gave each its own store and asserted only that lastSent() was
        // present - it would have passed with the window logic deleted entirely. Separating
        // policy from storage is what made the honest version writable.
        AlertRecordStore store = new InMemoryAlertRecordStore();
        new AlertLedger(store, Clock.fixed(DAY_ONE, ZoneOffset.UTC))
                .record(match(SupplyRisk.HIGH, "m1"));

        AlertLedger justInsideWindow = new AlertLedger(store,
                Clock.fixed(DAY_ONE.plus(AlertLedger.REPEAT_AFTER).minusSeconds(1),
                        ZoneOffset.UTC));
        assertThat(justInsideWindow.shouldSend(match(SupplyRisk.HIGH, "m1")))
                .as("still inside the repeat window - stay quiet")
                .isFalse();

        AlertLedger justOutsideWindow = new AlertLedger(store,
                Clock.fixed(DAY_ONE.plus(AlertLedger.REPEAT_AFTER).plusSeconds(1),
                        ZoneOffset.UTC));
        assertThat(justOutsideWindow.shouldSend(match(SupplyRisk.HIGH, "m1")))
                .as("past the repeat window - remind them")
                .isTrue();
    }

    @Test
    @DisplayName("what was recorded survives being read back through a different ledger")
    void storeOutlivesTheLedgerInstance() {
        // The v0.2 bug in miniature. The ledger object is per-run; the record of what we
        // told someone must outlive it, or every restart re-sends everything.
        AlertRecordStore store = new InMemoryAlertRecordStore();
        new AlertLedger(store, Clock.fixed(DAY_ONE, ZoneOffset.UTC))
                .record(match(SupplyRisk.HIGH, "m1"));

        AlertLedger freshInstance = new AlertLedger(store, Clock.fixed(DAY_ONE, ZoneOffset.UTC));
        assertThat(freshInstance.shouldSend(match(SupplyRisk.HIGH, "m1"))).isFalse();
    }

    @Test
    @DisplayName("different medications are tracked separately")
    void differentMedicationsAreSeparate() {
        AlertLedger ledger = ledgerAt(DAY_ONE);
        ledger.record(match(SupplyRisk.HIGH, "m1"));

        assertThat(ledger.shouldSend(match(SupplyRisk.HIGH, "m2"))).isTrue();
    }

    @Test
    @DisplayName("the ledger keys on identity, not on object identity")
    void keysOnIdentityNotObjectIdentity() {
        // Every sync builds fresh ShortageMatch objects, so anything object-identity-based
        // would de-duplicate nothing at all.
        AlertLedger ledger = ledgerAt(DAY_ONE);
        ledger.record(match(SupplyRisk.HIGH, "m1"));

        ShortageMatch equivalentButDistinctObject = match(SupplyRisk.HIGH, "m1");
        assertThat(ledger.shouldSend(equivalentButDistinctObject)).isFalse();
    }

    @Test
    @DisplayName("SupplyRisk is declared most-urgent-first, which the ledger relies on")
    void supplyRiskOrderingIsLoadBearing() {
        // The ledger compares urgency by ordinal. That is a real coupling between two files,
        // so it is asserted rather than assumed - reordering the enum would silently break
        // escalation detection.
        assertThat(SupplyRisk.CRITICAL.ordinal()).isLessThan(SupplyRisk.HIGH.ordinal());
        assertThat(SupplyRisk.HIGH.ordinal()).isLessThan(SupplyRisk.MEDIUM.ordinal());
        assertThat(SupplyRisk.MEDIUM.ordinal()).isLessThan(SupplyRisk.WATCH.ordinal());
    }
}
