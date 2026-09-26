package com.shadeclock.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves that neither a refused request nor a public one creates a session row.
 *
 * <p><b>The bug this guards against.</b> Spring Security's default {@code RequestCache} saves each
 * rejected request into a <em>new</em> session so it can replay it after login. That is sensible
 * for a server-rendered login page and wrong for a JSON API: it hands an anonymous caller a way to
 * insert one database row per refused request, with no credentials and no rate limit. RefillRadar
 * shipped with it enabled; {@code AuthHardening} now disables it for every app here.
 *
 * <p>ShadeClock needs the public half of this check more than the others, because it is the only
 * app with routes that answer 200 to a caller with no account at all. An open endpoint that minted
 * a session per request would be an unauthenticated write, dressed as a heat-index lookup.
 *
 * <p><b>Why this lives in its own class with a throwaway property.</b> It cannot share a context
 * with {@code SecurityIntegrationTest}, and the reason is worth recording.
 * {@code SecurityMockMvcRequestPostProcessors.csrf()} does not only decorate the request: it
 * reflectively replaces the live {@code CsrfFilter}'s {@code CsrfTokenRepository} inside the
 * shared {@code FilterChainProxy}, swapping this application's
 * {@code CookieCsrfTokenRepository} for its own session-backed {@code TestCsrfTokenRepository}.
 * That swap is not undone at the end of the request, and {@code @SpringBootTest} caches contexts
 * across classes - so a single {@code csrf()} anywhere turns every later request in that context
 * into one that creates a session. Measured while building RenewalGuard, not assumed: three
 * anonymous requests produced 0 rows, one {@code csrf()} request followed by three more identical
 * anonymous requests produced 3, and printing the filter's repository before and after showed
 * exactly that substitution.
 *
 * <p>So this class uses no {@code csrf()} at all, and the {@code properties} below give it a
 * context key nothing else shares. The same behaviour is asserted against a real server by the
 * container smoke test in CI, which is the version that proves it outside a mock.
 */
@SpringBootTest(properties = "shadeclock.test.isolated-context=anonymous-sessions")
@AutoConfigureMockMvc
class AnonymousSessionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private int sessionRows() {
        return jdbc.queryForObject("SELECT count(*) FROM SPRING_SESSION", Integer.class);
    }

    @Test
    @DisplayName("ten refused requests create zero sessions")
    void refusedTrafficCreatesNoSessions() throws Exception {
        jdbc.execute("TRUNCATE TABLE SPRING_SESSION CASCADE");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/me/crews")).andExpect(status().isUnauthorized());
        }

        assertThat(sessionRows())
                .as("an anonymous caller must not be able to insert session rows")
                .isZero();
    }

    @Test
    @DisplayName("ten successful public requests create zero sessions either")
    void publicTrafficCreatesNoSessions() throws Exception {
        jdbc.execute("TRUNCATE TABLE SPRING_SESSION CASCADE");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/heat-index")
                            .param("temperatureF", "95").param("humidity", "60"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/jurisdictions")).andExpect(status().isOk());
        }

        assertThat(sessionRows())
                .as("a public endpoint answering 200 must still not mint sessions")
                .isZero();
    }
}
