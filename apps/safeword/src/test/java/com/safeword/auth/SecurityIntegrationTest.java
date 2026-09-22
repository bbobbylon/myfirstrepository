package com.safeword.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.safeword.domain.Account;
import com.safeword.store.AccountRepository;
import com.safeword.support.Auth;
import com.safeword.support.DatabaseCleaner;
import com.safeword.support.TestAccounts;

/**
 * Proves v0.1's authorisation hole is closed - and that closing it did not lock the
 * emergency routes.
 *
 * <p>Through v0.1 a circle id in the URL was trusted, so anyone holding one could read a
 * family's setup and raise an alarm to them. Each test below is an attack that used to
 * succeed. They are written as attacks rather than feature checks because a feature test
 * tells you the happy path works; only an attack test tells you the unhappy one does not.
 *
 * <p>Two tests here guard the opposite failure. Over-locking this app is its own outage: if
 * the pause screen or the call check needed a login, the person being pressured by a
 * stranger on the phone would have to remember a password first.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseCleaner.class)
class SecurityIntegrationTest {

    private static final String ALICE_CIRCLE = """
            {"name":"The Alvarez family","passphraseAgreedOn":"2026-09-01","members":[
              {"name":"Rosa","role":"PROTECTED_PERSON","contact":"+15550000000"},
              {"name":"Alice","role":"RESPONDER","contact":"+15551112222"}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private Account alice;
    private Account mallory;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.clean();
        alice = accounts.save(TestAccounts.user("alice"));
        mallory = accounts.save(TestAccounts.user("mallory"));

        mockMvc.perform(post("/api/me/circle").with(Auth.as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ALICE_CIRCLE))
                .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- default deny

    @Test
    @DisplayName("every circle route refuses an unauthenticated caller with 401")
    void unauthenticatedCallersGet401() throws Exception {
        mockMvc.perform(get("/api/me/circle/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/me/circle").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/me/circle").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/me/circle/escalate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the old /api/circles/{id}/... routes are gone, not merely guarded")
    void theOldVulnerableRoutesNoLongerExist() throws Exception {
        // Authenticating as mallory takes the security filter out of the picture, so a 404
        // here means no handler is mapped at all. A guarded route that still accepts an id
        // is one forgotten check away from the original bug.
        String someId = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/circles/" + someId + "/status").with(Auth.as(mallory)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/circles/" + someId + "/escalate")
                        .with(Auth.as(mallory)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/circles").with(Auth.as(mallory)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ALICE_CIRCLE))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- the IDOR itself

    @Test
    @DisplayName("ATTACK: mallory cannot read alice's circle")
    void oneFamilyCannotReadAnothersCircle() throws Exception {
        // Mallory has no circle, so the only honest answer is 404 - not alice's.
        mockMvc.perform(get("/api/me/circle/status").with(Auth.as(mallory)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/me/circle/status").with(Auth.as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("The Alvarez family"));
    }

    @Test
    @DisplayName("ATTACK: mallory cannot raise an alarm to alice's family")
    void oneFamilyCannotEscalateToAnother() throws Exception {
        // The dangerous one. Today this only composes a message; once delivery is wired, a
        // stranger who could do this would be able to cry wolf at someone else's family
        // until they learn to ignore the alert this app exists to send.
        String body = mockMvc.perform(post("/api/me/circle/escalate")
                        .with(Auth.as(mallory)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aboutMoney\":true}"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("a refusal must not leak the names it refused to act on")
                .doesNotContain("Rosa").doesNotContain("Alice");
    }

    @Test
    @DisplayName("ATTACK: a second circle cannot quietly overwrite the first")
    void creatingASecondCircleIsRefused() throws Exception {
        mockMvc.perform(post("/api/me/circle").with(Auth.as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Replacement\",\"members\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("circle_exists"));

        // The responders alice actually has must still be there.
        mockMvc.perform(get("/api/me/circle/status").with(Auth.as(alice)))
                .andExpect(jsonPath("$.responderCount").value(1));
    }

    // ------------------------------------------------- the invariant, on every route

    @Test
    @DisplayName("THE security invariant: an update cannot smuggle in a passphrase either")
    void updateAlsoRefusesToAcceptAPassphrase() throws Exception {
        // v0.1 only ever tested this on create. A second write route is exactly where an
        // absolute rule quietly becomes a rule with one exception.
        mockMvc.perform(put("/api/me/circle").with(Auth.as(alice)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"The Alvarez family\",\"passphrase\":\"bluebird\","
                                + "\"members\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("passphrase_not_accepted"));
    }

    // ---------------------------------------------------------------- not over-locked

    @Test
    @DisplayName("the routes someone needs DURING a call stay open to anyone")
    void theEmergencyRoutesAreStillPublic() throws Exception {
        // Locking these would be the opposite mistake, and a quieter one: nothing would look
        // broken, the app would simply be useless at the only moment it matters.
        mockMvc.perform(get("/api/pause")).andExpect(status().isOk());
        mockMvc.perform(get("/api/scam-patterns")).andExpect(status().isOk());
        mockMvc.perform(get("/api/passphrase/instructions")).andExpect(status().isOk());

        // POST, anonymous, and deliberately WITHOUT a CSRF token: it reads and writes
        // nothing, so demanding a token would mean fetching one mid-scam-call.
        mockMvc.perform(post("/api/check-call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signals\":[\"FAMILY_EMERGENCY\",\"URGENCY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").exists());
    }

    @Test
    @DisplayName("a circle change without a CSRF token is refused")
    void csrfProtectionCoversCircleWrites() throws Exception {
        // Not disabled just because this returns JSON: the browser attaches the session
        // cookie to a cross-site POST whatever the content type is.
        mockMvc.perform(post("/api/me/circle/escalate").with(Auth.as(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- credentials

    @Test
    @DisplayName("registration cannot grant itself ADMIN")
    void registrationAlwaysCreatesAnOrdinaryUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"sneaky","password":"a-long-enough-passphrase",
                                 "role":"ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("a wrong password and an unknown user give the same answer")
    void loginDoesNotRevealWhetherAnAccountExists() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"real\",\"password\":\"a-long-enough-passphrase\"}"))
                .andExpect(status().isCreated());

        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"real\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String noSuchUser = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(noSuchUser);
    }

    @Test
    @DisplayName("ATTACK: the sixth guess at a password is refused, not answered")
    void guessingIsRateLimited() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"target\",\"password\":\"a-long-enough-passphrase\"}"))
                .andExpect(status().isCreated());

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"target\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"target\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("too_many_attempts"));
    }
}
