package com.shadeclock.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.commonauth.store.AccountRepository;
import com.commonauth.store.LoginAttemptStore;
import com.shadeclock.crew.Crew;
import com.shadeclock.crew.Worker;

/**
 * Proves the {@code memory} demo mode actually starts without a database.
 *
 * <p>Exists because the README tells people to run it, and a documented command nobody has
 * executed is how a README acquires one that has never worked. It is easy to break too: adding
 * any {@code @Repository} that needs a {@code DataSource}, or moving
 * {@code @EnableJpaRepositories} back onto the application class, would stop the context loading
 * under this profile while every other test stayed green - which is exactly what happened in
 * SafeWord and is why {@code JpaScanConfig} exists.
 */
@SpringBootTest
@ActiveProfiles("memory")
class MemoryProfileTest {

    @Autowired
    private CrewRepository crews;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LoginAttemptStore attempts;

    private static Crew crew(String id, String name) {
        return new Crew(id, name, "Route 12", 34.0522, -118.2437, "CA",
                List.of(new Worker(id + "-w1", "Named Worker", LocalDate.of(2026, 5, 1), null)));
    }

    @Test
    @DisplayName("the memory profile wires the in-memory stores and needs no database")
    void memoryProfileStartsWithNoDatabase() {
        // getTargetClass, not getClass: @Transactional wraps a bean in a CGLIB proxy, so the raw
        // class name would be "...$$SpringCGLIB$$0" and the assertion would fail for a reason
        // that has nothing to do with what is being asserted.
        assertThat(AopUtils.getTargetClass(crews).getSimpleName())
                .isEqualTo("InMemoryCrewRepository");
        assertThat(AopUtils.getTargetClass(accounts).getSimpleName())
                .isEqualTo("InMemoryAccountRepository");
        assertThat(AopUtils.getTargetClass(attempts).getSimpleName())
                .isEqualTo("InMemoryLoginAttemptStore");
    }

    @Test
    @DisplayName("the in-memory store enforces ownership exactly as the SQL one does")
    void inMemoryStoreEnforcesOwnership() {
        crews.save("alice", crew("crew-1", "Alice's Crew"));

        // Two implementations of one interface have to agree about who may read what, or a fast
        // test against this one proves nothing about the real one.
        assertThat(crews.findByIdAndOwner("crew-1", "alice")).isPresent();
        assertThat(crews.findByIdAndOwner("crew-1", "mallory")).isEmpty();
        assertThat(crews.findByOwner("mallory")).isEmpty();

        // And the same for the destructive path: a refused delete must not delete.
        assertThat(crews.deleteByIdAndOwner("crew-1", "mallory")).isFalse();
        assertThat(crews.findByIdAndOwner("crew-1", "alice")).isPresent();
        assertThat(crews.deleteByIdAndOwner("crew-1", "alice")).isTrue();
        assertThat(crews.findByIdAndOwner("crew-1", "alice")).isEmpty();
    }

    @Test
    @DisplayName("the in-memory store refuses a write under someone else's crew id, as JPA does")
    void inMemoryStoreRefusesHijack() {
        crews.save("alice", crew("crew-2", "Alice's Crew"));

        // The same hijack the JPA implementation had, and the same refusal. A plain put() here
        // would have replaced both the crew and its owner - so the agreement between the two
        // implementations is not cosmetic, it is the thing under test.
        // Asserted on the root cause and the message rather than the thrown type, because
        // Spring's @Repository exception translation wraps it as InvalidDataAccessApiUsageException
        // - and, measured rather than assumed, it does so under the memory profile too, even
        // though the JPA auto-configuration is excluded here. That is convenient: this assertion
        // and SecurityIntegrationTest's can be written identically for both implementations.
        assertThatThrownBy(() -> crews.save("mallory", crew("crew-2", "Mallory's Hijack")))
                .hasMessageContaining("belongs to another account")
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(crews.findByIdAndOwner("crew-2", "alice"))
                .hasValueSatisfying(c -> assertThat(c.name()).isEqualTo("Alice's Crew"));
        assertThat(crews.findByOwner("mallory")).isEmpty();
    }
}
