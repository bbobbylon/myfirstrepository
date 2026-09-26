package com.renewalguard.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.commonauth.store.AccountRepository;
import com.commonauth.store.LoginAttemptStore;
import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.BenefitProgram;
import com.renewalguard.domain.EnrollmentCategory;

/**
 * Proves the {@code memory} demo mode actually starts without a database.
 *
 * <p>Exists because the README tells people to run it, and a documented command nobody has
 * executed is how a README acquires one that has never worked. It is easy to break too: adding
 * any {@code @Repository} that needs a {@code DataSource}, or moving
 * {@code @EnableJpaRepositories} back onto the application class, would stop the context
 * loading under this profile while every other test stayed green - which is exactly what
 * happened in SafeWord and is why {@code JpaScanConfig} exists.
 */
@SpringBootTest
@ActiveProfiles("memory")
class MemoryProfileTest {

    @Autowired
    private BenefitCaseRepository cases;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private LoginAttemptStore attempts;

    @Test
    @DisplayName("the memory profile wires the in-memory stores and needs no database")
    void memoryProfileStartsWithNoDatabase() {
        // getTargetClass, not getClass: @Transactional wraps a bean in a CGLIB proxy, so the
        // raw class name would be "...$$SpringCGLIB$$0" and the assertion would fail for a
        // reason that has nothing to do with what is being asserted.
        assertThat(AopUtils.getTargetClass(cases).getSimpleName())
                .isEqualTo("InMemoryBenefitCaseRepository");
        assertThat(AopUtils.getTargetClass(accounts).getSimpleName())
                .isEqualTo("InMemoryAccountRepository");
        assertThat(AopUtils.getTargetClass(attempts).getSimpleName())
                .isEqualTo("InMemoryLoginAttemptStore");
    }

    @Test
    @DisplayName("the in-memory store enforces ownership exactly as the SQL one does")
    void inMemoryStoreEnforcesOwnership() {
        cases.save(new BenefitCase("case-1", "alice", BenefitProgram.MEDICAID, "CA",
                EnrollmentCategory.EXPANSION_ADULT, LocalDate.of(2027, 3, 1), null, null, 30));

        // Two implementations of one interface have to agree about who may read what, or a
        // fast test against this one proves nothing about the real one.
        assertThat(cases.findByIdAndUserId("case-1", "alice")).isPresent();
        assertThat(cases.findByIdAndUserId("case-1", "mallory")).isEmpty();
        assertThat(cases.findByUserId("mallory")).isEmpty();

        // And the same for the destructive path: a refused delete must not delete.
        assertThat(cases.deleteByIdAndUserId("case-1", "mallory")).isFalse();
        assertThat(cases.findByIdAndUserId("case-1", "alice")).isPresent();
        assertThat(cases.deleteByIdAndUserId("case-1", "alice")).isTrue();
        assertThat(cases.findByIdAndUserId("case-1", "alice")).isEmpty();
    }
}
