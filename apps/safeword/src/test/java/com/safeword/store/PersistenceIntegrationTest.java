package com.safeword.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.commonauth.domain.Account;
import com.commonauth.store.AccountRepository;
import com.safeword.domain.CircleMember;
import com.safeword.domain.FamilyCircle;
import com.safeword.domain.MemberRole;
import com.safeword.support.DatabaseCleaner;
import com.safeword.support.TestAccounts;

/**
 * Proves circles really land in PostgreSQL, with the constraints doing their job.
 *
 * <p>Every assertion here is one a {@code HashMap} would pass without trying: a date
 * surviving a round trip, an enum stored by name rather than position, a foreign key
 * cascading, a {@code CHECK} actually firing. Testing storage against a map proves nothing
 * about storage, which is the whole reason this file needs a database to run.
 *
 * <p>These tests commit for real and clean up afterwards rather than rolling back. A test
 * that never commits cannot catch a bad column mapping or a constraint violation, because
 * both surface at flush or commit.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
class PersistenceIntegrationTest {

    @Autowired
    private CircleRepository circles;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private Account owner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        owner = accounts.save(TestAccounts.user("owner"));
    }

    private static CircleMember member(String name, MemberRole role, String contact) {
        return new CircleMember(UUID.randomUUID().toString(), name, role, contact);
    }

    private FamilyCircle circleWith(List<CircleMember> members) {
        return new FamilyCircle(UUID.randomUUID().toString(), "The Family",
                LocalDate.of(2026, 9, 1), members);
    }

    @Test
    @DisplayName("a circle written by one call is read back whole by the next")
    void circlesSurviveTheRoundTrip() {
        circles.save(owner.id(), circleWith(List.of(
                member("Rosa", MemberRole.PROTECTED_PERSON, "+15550000000"),
                member("Ana", MemberRole.RESPONDER, "+15551112222"))));

        FamilyCircle stored = circles.findByOwner(owner.id()).orElseThrow();

        assertThat(stored.name()).isEqualTo("The Family");
        // The date matters: a LocalDate that comes back a day out is the classic timezone
        // bug, and it would silently change when a passphrase is judged stale.
        assertThat(stored.passphraseAgreedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(stored.members()).hasSize(2);
        assertThat(stored.responders()).singleElement()
                .satisfies(ana -> assertThat(ana.contact()).isEqualTo("+15551112222"));
    }

    @Test
    @DisplayName("saving a circle without a responder actually deletes that responder's row")
    void removingAResponderRemovesTheRow() {
        circles.save(owner.id(), circleWith(List.of(
                member("Ana", MemberRole.RESPONDER, "+15551112222"),
                member("Sam", MemberRole.RESPONDER, "+15559998888"))));

        circles.save(owner.id(), circleWith(List.of(
                member("Ana", MemberRole.RESPONDER, "+15551112222"))));

        // Counted in the database, not in the returned object: orphanRemoval is exactly the
        // setting whose absence leaves a "removed" responder still sitting in the table,
        // still looking like someone who would be called.
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM circle_members", Integer.class);
        assertThat(rows).isEqualTo(1);
        assertThat(circles.findByOwner(owner.id()).orElseThrow().responders()).hasSize(1);
    }

    @Test
    @DisplayName("closing an account takes its circle and members with it")
    void deletingAnAccountCascades() {
        circles.save(owner.id(), circleWith(List.of(
                member("Ana", MemberRole.RESPONDER, "+15551112222"))));

        jdbc.update("DELETE FROM users WHERE id = ?", owner.id());

        // ON DELETE CASCADE, enforced by the database rather than by remembering to write a
        // cleanup step. The alternative is rows describing an older adult's family that
        // nothing can reach and nobody can erase.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM circles", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM circle_members", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("the database refuses a responder with no contact route")
    void theCheckConstraintRefusesAnUnreachableResponder() {
        FamilyCircle stored = circles.save(owner.id(), circleWith(List.of(
                member("Ana", MemberRole.RESPONDER, "+15551112222"))));
        String circleId = jdbc.queryForObject("SELECT id FROM circles WHERE owner_account_id = ?",
                String.class, owner.id());

        // Inserted with raw SQL on purpose: CircleMember's constructor already rejects this,
        // so going through the domain would only test the domain. This asserts the copy of
        // the rule that a future import script or a hand-run UPDATE cannot get past.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO circle_members (id, circle_id, name, role, contact) "
                        + "VALUES (?, ?, ?, 'RESPONDER', NULL)",
                UUID.randomUUID().toString(), circleId, "Unreachable"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(stored.responders()).hasSize(1);
    }

    @Test
    @DisplayName("the member role is stored by name, never by position")
    void rolesAreStoredAsText() {
        circles.save(owner.id(), circleWith(List.of(
                member("Rosa", MemberRole.PROTECTED_PERSON, "+15550000000"))));

        // Stored by ordinal, inserting a value into the middle of MemberRole would silently
        // turn protected people into responders - and nothing in the database would notice.
        String role = jdbc.queryForObject("SELECT role FROM circle_members", String.class);
        assertThat(role).isEqualTo("PROTECTED_PERSON");
    }
}
