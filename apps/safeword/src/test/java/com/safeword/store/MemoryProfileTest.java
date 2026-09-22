package com.safeword.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.safeword.domain.CircleMember;
import com.safeword.domain.FamilyCircle;
import com.safeword.domain.MemberRole;

/**
 * Proves the documented {@code memory} demo mode actually starts without a database.
 *
 * <p>Exists because the README tells people to run it, and a documented command nobody has
 * executed is how a README acquires one that has never worked. It is easy to break too:
 * adding any {@code @Repository} that needs a {@code DataSource} would stop the context
 * loading under this profile while every other test stayed green.
 */
@SpringBootTest
@ActiveProfiles("memory")
class MemoryProfileTest {

    @Autowired
    private CircleRepository circles;

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
        assertThat(AopUtils.getTargetClass(circles).getSimpleName())
                .isEqualTo("InMemoryCircleRepository");
        assertThat(AopUtils.getTargetClass(accounts).getSimpleName())
                .isEqualTo("InMemoryAccountRepository");
        assertThat(AopUtils.getTargetClass(attempts).getSimpleName())
                .isEqualTo("InMemoryLoginAttemptStore");
    }

    @Test
    @DisplayName("it still stores and retrieves, so the demo is a real demo")
    void inMemoryStoreStillWorks() {
        FamilyCircle circle = new FamilyCircle("c1", "The Demo Family", LocalDate.of(2026, 9, 1),
                List.of(new CircleMember("m1", "Ana", MemberRole.RESPONDER, "+15551112222")));

        circles.save("account-1", circle);

        assertThat(circles.findByOwner("account-1")).hasValueSatisfying(stored ->
                assertThat(stored.responders()).hasSize(1));
        assertThat(circles.findByOwner("someone-else")).isEmpty();
    }
}
