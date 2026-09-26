package com.renewalguard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

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
import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.BenefitProgram;
import com.renewalguard.domain.EnrollmentCategory;
import com.renewalguard.store.BenefitCaseRepository;
import com.renewalguard.support.Auth;
import com.renewalguard.support.DatabaseCleaner;
import com.renewalguard.support.TestAccounts;

/**
 * Every test here is an attack that <b>used to succeed</b> against v0.1.
 *
 * <p>Written as attacks on purpose. "The ownership check works" is a claim about code; "mallory
 * cannot read alice's case" is a claim about behaviour, and only the second one still means
 * something after somebody refactors the controller. Two of these were verified by putting the
 * original bug back and confirming the test goes red - a test that has never failed is a test
 * whose value is unmeasured.
 *
 * <p>What v0.1 allowed, concretely: {@code GET /api/cases/&#123;caseId&#125;/status} performed
 * no check of any kind, {@code GET /api/users/&#123;userId&#125;/cases} listed whoever the URL
 * named, and {@code POST /api/cases} filed the case under whatever {@code userId} the body
 * contained. A benefits case discloses that a named person is enrolled in Medicaid, in a named
 * state, in a named eligibility category - which for this population can mean disclosing
 * disability, pregnancy or poverty to anyone who guessed an id.
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
    private BenefitCaseRepository cases;

    @Autowired
    private JdbcTemplate jdbc;

    private Account alice;
    private Account mallory;

    /** A case that really exists and really belongs to Alice. Mallory's target. */
    private String aliceCaseId;

    @BeforeEach
    void twoUsersAndOneCase() {
        databaseCleaner.clean();
        alice = accounts.save(TestAccounts.user("alice"));
        mallory = accounts.save(TestAccounts.user("mallory"));

        aliceCaseId = UUID.randomUUID().toString();
        cases.save(new BenefitCase(aliceCaseId, alice.id(), BenefitProgram.MEDICAID, "CA",
                EnrollmentCategory.EXPANSION_ADULT, LocalDate.of(2027, 3, 1), null, null, 30));
    }

    @Test
    @DisplayName("every case route refuses an unauthenticated caller with 401")
    void anonymousIsRefused() throws Exception {
        // 401, not a redirect to a login page. This is a JSON API: a 302 to /login is a
        // response an HTTP client cannot act on and a browser fetch() reports as a CORS
        // failure, hiding the real cause. AuthHardening sets the entry point that does this.
        mockMvc.perform(get("/api/me/cases")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/cases/" + aliceCaseId + "/status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/cases/" + aliceCaseId + "/reminder"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/me/cases/" + aliceCaseId).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/cases").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stateCode":"CA","renewalDueOn":"2027-03-01"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the v0.1 routes are gone, not merely guarded")
    void oldRoutesNoLongerExist() throws Exception {
        // Authenticated, so a 401 cannot be what makes these pass - the paths genuinely do
        // not map to a handler any more. Worth asserting: leaving the old route in place
        // behind a check would mean the vulnerable shape still existed, one edit from
        // working again. 404 here is the route being absent.
        mockMvc.perform(get("/api/cases/" + aliceCaseId + "/status").with(Auth.as(alice)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/" + alice.id() + "/cases").with(Auth.as(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ATTACK: mallory cannot read alice's case")
    void malloryCannotReadAlicesCase() throws Exception {
        // The exact request that worked in v0.1, with a real case id, from a logged-in
        // account that is not the owner.
        mockMvc.perform(get("/api/me/cases/" + aliceCaseId + "/status").with(Auth.as(mallory)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/cases/" + aliceCaseId + "/reminder").with(Auth.as(mallory)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ATTACK: mallory cannot delete alice's case, and it survives the attempt")
    void malloryCannotDeleteAlicesCase() throws Exception {
        mockMvc.perform(delete("/api/me/cases/" + aliceCaseId).with(Auth.as(mallory)).with(csrf()))
                .andExpect(status().isNotFound());

        // The refusal is not enough on its own: assert the row is still there. A delete that
        // returns 404 *after* removing the case would pass the status assertion and still
        // have destroyed somebody's tracked deadline.
        assertThat(cases.findByIdAndUserId(aliceCaseId, alice.id())).isPresent();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM benefit_cases WHERE id = ?", Integer.class, aliceCaseId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("ATTACK: the 404 for someone else's case is indistinguishable from a missing one")
    void refusalIsNotAnExistenceOracle() throws Exception {
        // This is the assertion that stops the fix from leaking the thing it protects. If a
        // real-but-not-yours id answered differently from a fabricated one - 403 vs 404, or
        // even a different body - then enumerating ids would still reveal which cases exist,
        // and "this person has a Medicaid case" is itself the sensitive fact.
        String realButNotMine = aliceCaseId;
        String pureFiction = UUID.randomUUID().toString();

        var refusedReal = mockMvc.perform(
                        get("/api/me/cases/" + realButNotMine + "/status").with(Auth.as(mallory)))
                .andReturn().getResponse();
        var refusedFake = mockMvc.perform(
                        get("/api/me/cases/" + pureFiction + "/status").with(Auth.as(mallory)))
                .andReturn().getResponse();

        assertThat(refusedReal.getStatus()).isEqualTo(refusedFake.getStatus());
        assertThat(refusedReal.getContentAsString()).isEqualTo(refusedFake.getContentAsString());
    }

    @Test
    @DisplayName("ATTACK: mallory's case list contains only mallory's cases")
    void listingIsScopedToTheSession() throws Exception {
        mockMvc.perform(get("/api/me/cases").with(Auth.as(mallory)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/me/cases").with(Auth.as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(aliceCaseId));
    }

    @Test
    @DisplayName("ATTACK: a userId in the payload cannot file a case under someone else")
    void suppliedOwnerIsIgnored() throws Exception {
        // Spring Boot disables FAIL_ON_UNKNOWN_PROPERTIES, so this extra field is silently
        // dropped rather than rejected. That is the safe direction, but "safe because Jackson
        // ignores it" is exactly the kind of assumption that should be nailed down by a test:
        // a future @JsonAnySetter or a switch to a Map-based payload would reopen it.
        mockMvc.perform(post("/api/cases").with(Auth.as(mallory)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","stateCode":"NV","renewalDueOn":"2027-06-01"}
                                """.formatted(alice.id())))
                .andExpect(status().isCreated())
                // Owned by the session, not by the body.
                .andExpect(jsonPath("$.userId").value(mallory.id()));

        // And Alice's list did not grow.
        mockMvc.perform(get("/api/me/cases").with(Auth.as(alice)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("a write with no CSRF token is refused")
    void writesNeedACsrfToken() throws Exception {
        // Authenticated but tokenless: this is the shape of a cross-site request riding a
        // logged-in user's cookie. SameSite=strict is the first defence and this is the
        // second; two independent mechanisms because the first depends on the browser.
        mockMvc.perform(post("/api/cases").with(Auth.as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stateCode":"CA","renewalDueOn":"2027-03-01"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/me/cases/" + aliceCaseId).with(Auth.as(alice)))
                .andExpect(status().isForbidden());

        // The delete was refused before reaching the handler, so the case is intact.
        assertThat(cases.findByIdAndUserId(aliceCaseId, alice.id())).isPresent();
    }

}
