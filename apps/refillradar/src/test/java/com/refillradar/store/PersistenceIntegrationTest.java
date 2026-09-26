package com.refillradar.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.commonauth.store.AccountRepository;
import com.refillradar.alert.AlertLedger;
import com.refillradar.alert.AlertRecordStore;
import com.refillradar.domain.Medication;
import com.refillradar.domain.SupplyRisk;
import com.refillradar.support.DatabaseCleaner;
import com.refillradar.support.TestAccounts;

/**
 * Proves that v0.3 storage actually reaches PostgreSQL and comes back unchanged.
 *
 * <p>Every assertion here is one the in-memory implementations passed trivially and the real
 * ones might not: a date that survives a round trip, a query that is scoped to one user, an
 * enum stored as its name rather than its position, a constraint the database enforces.
 * Testing persistence against a map proves nothing about persistence.
 *
 * <p>Requires a running PostgreSQL - see {@code README.md}.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
class PersistenceIntegrationTest {

    private static final LocalDate LAST_FILLED = LocalDate.of(2026, 9, 1);

    @Autowired
    private MedicationRepository medications;

    @Autowired
    private ContactRepository contacts;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private AlertRecordStore alertRecords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void emptyTheDatabase() {
        databaseCleaner.clean();
    }

    /**
     * Stores a medication, creating its owning account first.
     *
     * <p>Since V2, {@code medications.user_id} is a foreign key. A test that skips this step
     * gets a constraint violation rather than a stored row - which is the constraint doing
     * its job, because before V2 a medication could name an account that never existed.
     */
    private Medication medicationOwnedBy(String id, String userId) {
        if (accounts.findById(userId).isEmpty()) {
            accounts.save(TestAccounts.user(userId, userId + "-name"));
        }
        return new Medication(id, userId, "Keppra 500mg", "levetiracetam", LAST_FILLED, 30);
    }

    @Test
    @DisplayName("the configured repository is the PostgreSQL one, not the in-memory one")
    void postgresIsTheDefault() {
        // If this ever fails, the rest of this class is testing a HashMap and proving
        // nothing. The whole point of v0.3 is that the default cannot be the amnesiac one.
        //
        // AopUtils.getTargetClass unwraps the proxy. Both beans are @Transactional, so
        // getClass() returns "JpaMedicationRepository$$SpringCGLIB$$0" - Spring's generated
        // subclass that opens the transaction before delegating. Asserting on getClass()
        // directly fails for a reason that has nothing to do with what is being tested.
        assertThat(AopUtils.getTargetClass(medications).getSimpleName())
                .isEqualTo("JpaMedicationRepository");
        assertThat(AopUtils.getTargetClass(alertRecords).getSimpleName())
                .isEqualTo("JpaAlertRecordStore");
    }

    @Test
    @DisplayName("a saved medication comes back with every field intact")
    void medicationRoundTrips() {
        medications.save(medicationOwnedBy("m1", "robert"));

        Medication found = medications.findByUserId("robert").getFirst();

        assertThat(found.id()).isEqualTo("m1");
        assertThat(found.displayName()).isEqualTo("Keppra 500mg");
        assertThat(found.searchTerm()).isEqualTo("levetiracetam");
        // The date is the field most likely to drift on a round trip through a driver.
        assertThat(found.lastFilledOn()).isEqualTo(LAST_FILLED);
        assertThat(found.daysSupply()).isEqualTo(30);
        assertThat(found.projectedRunOutDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("one user can never see another user's medications")
    void findByUserIdIsScopedToOneUser() {
        // A security property, not a convenience. It is asserted here rather than assumed
        // from the method name, because the method name is generated into SQL by Spring Data
        // and a rename would change the query silently.
        medications.save(medicationOwnedBy("m1", "robert"));
        medications.save(medicationOwnedBy("m2", "someone-else"));

        assertThat(medications.findByUserId("robert")).hasSize(1);
        assertThat(medications.findByUserId("robert").getFirst().id()).isEqualTo("m1");
        assertThat(medications.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("saving the same id twice replaces rather than duplicating")
    void saveIsIdempotentOnId() {
        medications.save(medicationOwnedBy("m1", "robert"));
        medications.save(new Medication("m1", "robert", "Keppra 750mg", "levetiracetam",
                LAST_FILLED, 30));

        assertThat(medications.findByUserId("robert")).hasSize(1);
        assertThat(medications.findByUserId("robert").getFirst().displayName())
                .isEqualTo("Keppra 750mg");
    }

    @Test
    @DisplayName("deleting an unknown id reports false rather than pretending")
    void deleteReportsWhetherAnythingWasRemoved() {
        medications.save(medicationOwnedBy("m1", "robert"));

        assertThat(medications.deleteById("does-not-exist")).isFalse();
        assertThat(medications.deleteById("m1")).isTrue();
        assertThat(medications.findByUserId("robert")).isEmpty();
    }

    @Test
    @DisplayName("the database rejects a non-positive daysSupply even when the record is bypassed")
    void databaseEnforcesTheDaysSupplyInvariant() {
        // Medication's compact constructor already rejects this, so the only way to reach
        // the constraint is to go round it - exactly what a bulk import or a repair script
        // would do. This asserts the defence in depth is real and not decorative.
        accounts.save(TestAccounts.user("robert", "robert-name"));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO medications (id, user_id, display_name, search_term, "
                        + "last_filled_on, days_supply) VALUES (?, ?, ?, ?, ?, ?)",
                "bad", "robert", "Keppra", "levetiracetam", LAST_FILLED, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("medications_days_supply_positive");
    }

    @Test
    @DisplayName("a contact round-trips and isReachable reflects the database")
    void contactRoundTrips() {
        accounts.save(TestAccounts.user("robert", "robert-name"));
        assertThat(contacts.isReachable("robert")).isFalse();

        contacts.setEmail("robert", "robert@example.invalid");

        assertThat(contacts.isReachable("robert")).isTrue();
        assertThat(contacts.findEmail("robert")).contains("robert@example.invalid");
        assertThat(contacts.findEmail("nobody")).isEmpty();
    }

    @Test
    @DisplayName("the alert ledger survives, which is the bug v0.3 exists to fix")
    void alertRecordSurvivesInTheDatabase() {
        Instant sentAt = Instant.parse("2026-09-21T03:00:00Z");
        alertRecords.put("robert|m1|levetiracetam",
                new AlertLedger.Entry(SupplyRisk.CRITICAL, sentAt));

        AlertLedger.Entry found = alertRecords.find("robert|m1|levetiracetam").orElseThrow();

        assertThat(found.risk()).isEqualTo(SupplyRisk.CRITICAL);
        assertThat(found.sentAt()).isEqualTo(sentAt);
    }

    @Test
    @DisplayName("SupplyRisk is stored as its NAME, so reordering the enum cannot corrupt rows")
    void riskIsStoredAsAString() {
        // Guards the @Enumerated(EnumType.STRING) choice. With the JPA default (ORDINAL) a
        // stored CRITICAL would read back as whatever now sits at that position, and the
        // ledger's escalation check would suppress an alert it should send.
        alertRecords.put("robert|m1|levetiracetam",
                new AlertLedger.Entry(SupplyRisk.CRITICAL, Instant.parse("2026-09-21T03:00:00Z")));

        String stored = jdbc.queryForObject(
                "SELECT risk FROM alert_records WHERE alert_key = ?", String.class,
                "robert|m1|levetiracetam");

        assertThat(stored).isEqualTo("CRITICAL");
    }
}
