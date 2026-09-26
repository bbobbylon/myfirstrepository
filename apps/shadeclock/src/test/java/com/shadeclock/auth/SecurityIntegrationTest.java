package com.shadeclock.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.commonauth.domain.Account;
import com.commonauth.store.AccountRepository;
import com.shadeclock.crew.Crew;
import com.shadeclock.crew.Worker;
import com.shadeclock.store.CrewRepository;
import com.shadeclock.support.Auth;
import com.shadeclock.support.DatabaseCleaner;
import com.shadeclock.support.TestAccounts;

/**
 * Every test here is an attack that <b>used to succeed</b> against v0.1.
 *
 * <p>Written as attacks on purpose. "The ownership check works" is a claim about code; "a stranger
 * cannot list every crew" is a claim about behaviour, and only the second still means something
 * after somebody refactors the controller. Several were verified by putting the original bug back
 * and confirming the test goes red - a test that has never failed is a test whose value is
 * unmeasured.
 *
 * <p>What v0.1 allowed, concretely: {@code GET /api/crews} called {@code findAll()} with no
 * credentials, so one request returned every crew in the system;
 * {@code GET /api/crews/&#123;id&#125;/schedule} checked nothing; and {@code POST /api/crews}
 * created crews that belonged to nobody. The listing is the one that matters most, because there
 * was no id to guess - a roster naming workers, their absence-derived dates, and the coordinates
 * of their work site was simply readable by anyone who could reach the port.
 *
 * <p>The last two tests guard the <em>opposite</em> failure. Over-locking looks like nothing at
 * all: the app is merely useless at the moment it exists for.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseCleaner.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CrewRepository crews;

    @Autowired
    private JdbcTemplate jdbc;

    private Account alice;
    private Account mallory;

    /** A crew that really exists and really belongs to Alice. Mallory's target. */
    private String aliceCrewId;

    @BeforeEach
    void twoSupervisorsAndOneCrew() {
        databaseCleaner.clean();
        alice = accounts.save(TestAccounts.user("alice"));
        mallory = accounts.save(TestAccounts.user("mallory"));

        aliceCrewId = UUID.randomUUID().toString();
        crews.save(alice.id(), new Crew(aliceCrewId, "Alice's Paving Crew", "Route 12",
                34.0522, -118.2437, "CA",
                List.of(new Worker(UUID.randomUUID().toString(), "Named Worker",
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 20)))));
    }

    @Test
    @DisplayName("ATTACK: the wide-open roster listing is gone, not merely guarded")
    void theOpenListingNoLongerExists() throws Exception {
        // THE v0.1 BUG. No id, no credentials, every crew in the system. Authenticated below so
        // a 401 cannot be what makes this pass: there is genuinely no GET handler for this path
        // any more. Leaving the route in place behind a check would mean the vulnerable shape
        // still existed, one edit from working again.
        //
        // 405 rather than 404, and that is the correct answer rather than a compromise: POST
        // /api/crews still lives at this exact path, so Spring matches the path, finds no GET
        // mapping and reports Method Not Allowed. It discloses nothing the POST route did not
        // already, and it is positive evidence that the GET listing is absent - a 404 here
        // would have meant the whole path had gone, which is not what was intended.
        mockMvc.perform(get("/api/crews").with(Auth.as(alice)))
                .andExpect(status().isMethodNotAllowed());

        // And anonymously - which is how it was actually exploitable. This one answers 401
        // rather than 405, and the difference is instructive: anyRequest().authenticated()
        // makes the security filter reject the request before the dispatcher ever gets to
        // decide that GET has no handler. The caller who could once read every roster now does
        // not even reach the routing layer.
        mockMvc.perform(get("/api/crews")).andExpect(status().isUnauthorized());

        // The old schedule route has no POST sibling, so that one really is a 404.
        mockMvc.perform(get("/api/crews/" + aliceCrewId + "/schedule").with(Auth.as(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("every crew route refuses an unauthenticated caller with 401")
    void anonymousIsRefused() throws Exception {
        // 401, not a redirect to a login page. This is a JSON API: a 302 to /login is a response
        // an HTTP client cannot act on and a browser fetch() reports as a CORS failure, hiding
        // the real cause. AuthHardening sets the entry point that does this.
        mockMvc.perform(get("/api/me/crews")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/crews/" + aliceCrewId + "/schedule"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/me/crews/" + aliceCrewId).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/crews").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","siteLabel":"S","latitude":34.0,"longitude":-118.0,
                                 "jurisdiction":"CA","workers":[]}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ATTACK: mallory cannot read alice's crew or its schedule")
    void malloryCannotReadAlicesCrew() throws Exception {
        mockMvc.perform(get("/api/me/crews/" + aliceCrewId + "/schedule")
                        .param("date", "2026-07-15").with(Auth.as(mallory)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ATTACK: mallory's listing contains only mallory's crews")
    void listingIsScopedToTheSession() throws Exception {
        mockMvc.perform(get("/api/me/crews").with(Auth.as(mallory)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/me/crews").with(Auth.as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(aliceCrewId))
                // The worker's name is in there - which is exactly why the listing had to close.
                .andExpect(jsonPath("$[0].workers[0].name").value("Named Worker"));
    }

    @Test
    @DisplayName("ATTACK: mallory cannot delete alice's crew, and it survives the attempt")
    void malloryCannotDeleteAlicesCrew() throws Exception {
        mockMvc.perform(delete("/api/me/crews/" + aliceCrewId)
                        .with(Auth.as(mallory)).with(csrf()))
                .andExpect(status().isNotFound());

        // The refusal is not enough on its own: assert the rows are still there. A delete that
        // returns 404 *after* removing the crew would pass the status assertion and still have
        // destroyed a roster.
        assertThat(crews.findByIdAndOwner(aliceCrewId, alice.id())).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crews WHERE id = ?",
                Integer.class, aliceCrewId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crew_workers WHERE crew_id = ?",
                Integer.class, aliceCrewId)).isEqualTo(1);
    }

    @Test
    @DisplayName("ATTACK: mallory cannot overwrite alice's crew by reusing its id")
    void malloryCannotHijackAlicesCrewById() throws Exception {
        // The subtlest one, and the one that caught a real bug in the first version of
        // JpaCrewRepository. save() looks the row up before writing so an update can replace
        // the worker list in place. Making that lookup owner-scoped - the obvious security fix
        // - is what created the hole: when Mallory's scoped lookup found nothing, the code
        // treated the id as NEW and called rows.save(), and JPA's save() on an existing primary
        // key is a MERGE. It UPDATEd Alice's row and reassigned owner_account_id to Mallory.
        // The owner-scoped read made the write unsafe by hiding the row it was about to clobber.
        //
        // It now refuses outright. Loudly, not silently: crew ids are server-generated UUIDs,
        // so reaching this branch means a bug or an attack, and a quiet no-op would hide both.
        // Spring's @Repository exception translation wraps the IllegalStateException, so the
        // assertion checks the root cause rather than the wrapper's type.
        assertThatThrownBy(() -> crews.save(mallory.id(),
                new Crew(aliceCrewId, "Mallory's Hijack", "Nowhere", 0.0, 0.0, "CA", List.of())))
                .hasMessageContaining("belongs to another account")
                .hasRootCauseInstanceOf(IllegalStateException.class);

        // And the point of the whole test: Alice's crew is untouched, still hers, workers intact.
        assertThat(crews.findByIdAndOwner(aliceCrewId, alice.id()))
                .hasValueSatisfying(crew -> {
                    assertThat(crew.name()).isEqualTo("Alice's Paving Crew");
                    assertThat(crew.workers()).hasSize(1);
                });
        assertThat(crews.findByOwner(mallory.id())).isEmpty();
    }

    @Test
    @DisplayName("ATTACK: the 404 for someone else's crew is indistinguishable from a missing one")
    void refusalIsNotAnExistenceOracle() throws Exception {
        // This is the assertion that stops the fix from leaking the thing it protects. If a
        // real-but-not-yours id answered differently from a fabricated one, enumerating ids
        // would still reveal which crews exist.
        var refusedReal = mockMvc.perform(get("/api/me/crews/" + aliceCrewId + "/schedule")
                        .with(Auth.as(mallory)))
                .andReturn().getResponse();
        var refusedFake = mockMvc.perform(
                        get("/api/me/crews/" + UUID.randomUUID() + "/schedule")
                                .with(Auth.as(mallory)))
                .andReturn().getResponse();

        assertThat(refusedReal.getStatus()).isEqualTo(refusedFake.getStatus());
        assertThat(refusedReal.getContentAsString()).isEqualTo(refusedFake.getContentAsString());
    }

    @Test
    @DisplayName("a write with no CSRF token is refused")
    void writesNeedACsrfToken() throws Exception {
        // Authenticated but tokenless: the shape of a cross-site request riding a logged-in
        // user's cookie. SameSite=strict is the first defence and this is the second; two
        // independent mechanisms because the first depends on the browser.
        mockMvc.perform(post("/api/crews").with(Auth.as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","siteLabel":"S","latitude":34.0,"longitude":-118.0,
                                 "jurisdiction":"CA","workers":[]}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/me/crews/" + aliceCrewId).with(Auth.as(alice)))
                .andExpect(status().isForbidden());

        assertThat(crews.findByIdAndOwner(aliceCrewId, alice.id())).isPresent();
    }

    @Test
    @DisplayName("THE OPPOSITE FAILURE: the heat index stays open to anyone, with no account")
    void heatIndexStaysPublic() throws Exception {
        // Over-locking is the quieter bug: nothing looks broken, the app is just useless at 2pm
        // on a roof. A worker checking whether conditions are dangerous must not meet a login
        // wall, and this endpoint holds nobody's data - it is a formula.
        mockMvc.perform(get("/api/heat-index")
                        .param("temperatureF", "90").param("humidity", "70"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatIndexF").value(106))
                .andExpect(jsonPath("$.band").value("DANGER"))
                // The uncertainty travels with the number, anonymously or not.
                .andExpect(jsonPath("$.statedErrorF").value(1.3))
                .andExpect(jsonPath("$.limitation").exists());
    }

    @Test
    @DisplayName("THE OPPOSITE FAILURE: the jurisdiction list stays open, and creates no session")
    void jurisdictionsStayPublic() throws Exception {
        mockMvc.perform(get("/api/jurisdictions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasItems("CA", "US-BASELINE")));

        // The matching "and it creates no session" assertion deliberately lives in
        // AnonymousSessionTest instead, in its own context. It cannot work here: csrf() is used
        // elsewhere in this class, and it reflectively swaps the live CsrfFilter's
        // CookieCsrfTokenRepository for a session-backed test one inside the shared
        // FilterChainProxy - a substitution that outlives the request and makes every later
        // request in this cached context create a session. See that class for the measurements.
    }
}
