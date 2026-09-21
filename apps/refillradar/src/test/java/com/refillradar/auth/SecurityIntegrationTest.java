package com.refillradar.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.refillradar.domain.Account;
import com.refillradar.domain.Medication;
import com.refillradar.store.AccountRepository;
import com.refillradar.store.MedicationRepository;
import com.refillradar.support.Auth;
import com.refillradar.support.DatabaseCleaner;
import com.refillradar.support.TestAccounts;

/**
 * Proves the v0.3 authorisation hole is closed, and stays closed.
 *
 * <p>Through v0.3 every endpoint took a {@code userId} from the request and trusted it, so
 * any caller could read, extend or delete any user's medication list. Each test below is an
 * attack that used to succeed. They are written as attacks rather than as feature checks
 * because a feature test tells you the happy path works; only an attack test tells you the
 * unhappy one does not.
 *
 * <p>One check is deliberately NOT here: that an anonymous request creates no session row.
 * Before the fix in {@code SecurityConfig}, 20 unauthenticated GETs produced 20 rows in
 * {@code SPRING_SESSION} - an unauthenticated way to grow the database. MockMvc keeps its
 * own session bookkeeping and does not reproduce a real servlet container's here, so that
 * assertion lives in the container smoke test in {@code refillradar-ci.yml}, against a real
 * server over real HTTP, which is where the behaviour was measured in the first place.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseCleaner.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private MedicationRepository medications;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private Account alice;
    private Account mallory;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        alice = accounts.save(TestAccounts.user("alice"));
        mallory = accounts.save(TestAccounts.user("mallory"));
    }

    private Medication keppraFor(Account owner) {
        return medications.save(new Medication(UUID.randomUUID().toString(), owner.id(),
                "Keppra 500mg", "levetiracetam", LocalDate.of(2026, 9, 1), 30));
    }

    // ---------------------------------------------------------------- default deny

    @Test
    @DisplayName("every data endpoint refuses an unauthenticated caller with 401")
    void unauthenticatedCallersGet401() throws Exception {
        // 401 and not a 302 to a login page: an API client cannot tell a redirect from
        // success without parsing the body it did not expect.
        mockMvc.perform(get("/api/me/medications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/supply-check")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/contact")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sync/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/medications").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the old /api/users/{id}/... routes are gone, not merely guarded")
    void theOldVulnerableRoutesNoLongerExist() throws Exception {
        // A guarded route that still accepts an id is one forgotten check away from the
        // original bug. Authenticating as mallory removes the security filter from the
        // picture, so a 404 here means no handler is mapped at all.
        mockMvc.perform(get("/api/users/" + alice.id() + "/supply-check").with(Auth.as(mallory)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/" + alice.id() + "/medications").with(Auth.as(mallory)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/" + alice.id() + "/contact").with(Auth.as(mallory)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- the IDOR itself

    @Test
    @DisplayName("ATTACK: mallory cannot read alice's medication list")
    void oneUserCannotReadAnothersMedications() throws Exception {
        keppraFor(alice);

        mockMvc.perform(get("/api/me/medications").with(Auth.as(mallory)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/me/medications").with(Auth.as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("ATTACK: mallory cannot see alice's shortages in a supply check")
    void oneUserCannotSeeAnothersSupplyCheck() throws Exception {
        keppraFor(alice);

        mockMvc.perform(get("/api/me/supply-check").with(Auth.as(mallory)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicationsChecked").value(0));
    }

    @Test
    @DisplayName("ATTACK: mallory cannot delete alice's medication, and gets 404 not 403")
    void oneUserCannotDeleteAnothersMedication() throws Exception {
        Medication alicesMedication = keppraFor(alice);

        // 404, deliberately. A 403 would confirm the id is real, letting an attacker map
        // other people's records one guess at a time. If it is not yours, it is not there.
        mockMvc.perform(delete("/api/medications/" + alicesMedication.id())
                        .with(Auth.as(mallory)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(medications.findByUserId(alice.id()))
                .as("alice's medication must still exist after mallory's attempt")
                .hasSize(1);

        mockMvc.perform(delete("/api/medications/" + alicesMedication.id())
                        .with(Auth.as(alice)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("ATTACK: a userId smuggled into the request body is ignored")
    void suppliedUserIdCannotOverrideTheSession() throws Exception {
        // MedicationRequest has no userId field any more, but a client can still put one in
        // the JSON. This asserts it changes nothing - the row is filed under the session's
        // account. That is the difference between removing a parameter and merely
        // documenting that it is ignored.
        String payloadWithSmuggledOwner = """
                {
                  "userId": "%s",
                  "displayName": "Keppra 500mg",
                  "searchTerm": "levetiracetam",
                  "lastFilledOn": "2026-09-01",
                  "daysSupply": 30
                }
                """.formatted(alice.id());

        mockMvc.perform(post("/api/medications")
                        .with(Auth.as(mallory)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithSmuggledOwner))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(mallory.id()));

        assertThat(medications.findByUserId(alice.id()))
                .as("nothing may be filed under alice by anyone but alice")
                .isEmpty();
    }

    @Test
    @DisplayName("ATTACK: mallory cannot redirect alice's alerts to her own address")
    void oneUserCannotHijackAnothersContactAddress() throws Exception {
        mockMvc.perform(post("/api/me/contact")
                        .with(Auth.as(mallory)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"mallory@example.invalid\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/contact").with(Auth.as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(false));
    }

    // ---------------------------------------------------------------- privilege

    @Test
    @DisplayName("ATTACK: an ordinary user cannot trigger a sync")
    void ordinaryUsersCannotReachAdminEndpoints() throws Exception {
        // Open to anyone in v0.3: a free way to hammer the FDA and to mail every user.
        mockMvc.perform(post("/api/admin/sync").with(Auth.as(mallory)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin can trigger a sync")
    void adminsCanReachAdminEndpoints() throws Exception {
        Account operator = accounts.save(TestAccounts.admin("operator"));

        mockMvc.perform(post("/api/admin/sync").with(Auth.as(operator)).with(csrf()))
                .andExpect(status().isOk());
    }

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

        assertThat(accounts.findByUsername("sneaky").orElseThrow().isAdmin()).isFalse();
    }

    // ---------------------------------------------------------------- credentials

    @Test
    @DisplayName("a registered password is hashed, and the hash is never returned")
    void passwordsAreHashedAndNeverSerialised() throws Exception {
        String password = "correct-horse-battery-staple";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newcomer\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(password))))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Account stored = accounts.findByUsername("newcomer").orElseThrow();
        assertThat(stored.passwordHash()).doesNotContain(password);
        assertThat(stored.passwordHash()).startsWith("{bcrypt}$2a$");
        assertThat(passwordEncoder.matches(password, stored.passwordHash())).isTrue();
    }

    @Test
    @DisplayName("a wrong password and an unknown user give the same answer")
    void loginDoesNotRevealWhetherAnAccountExists() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"real\",\"password\":\"a-long-enough-passphrase\"}"))
                .andExpect(status().isCreated());

        // Different responses would let anyone enumerate who has an account here, which for
        // this application is who is managing a medication.
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
    @DisplayName("a password longer than BCrypt reads is rejected, not silently truncated")
    void overlongPasswordsAreRejected() throws Exception {
        // BCrypt ignores everything past 72 bytes. Accepting a longer one would mean two
        // different passphrases sharing a 72-byte prefix both open the account, with the
        // user having no way to find out.
        String tooLong = "x".repeat(AuthController.MAX_PASSWORD_BYTES + 1);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"longpass\",\"password\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password_too_long"));
    }

    @Test
    @DisplayName("a short password is rejected")
    void shortPasswordsAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"shorty\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("usernames are taken case-insensitively")
    void usernameUniquenessIgnoresCase() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Robert\",\"password\":\"a-long-enough-passphrase\"}"))
                .andExpect(status().isCreated());

        // Otherwise "Robert" and "robert" are two accounts, and one of them is a
        // convincing impersonation of the other.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"robert\",\"password\":\"a-long-enough-passphrase\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("username_taken"));
    }

    // ---------------------------------------------------------------- CSRF

    @Test
    @DisplayName("a state-changing request without a CSRF token is refused")
    void csrfProtectionIsOn() throws Exception {
        // Not disabled just because this returns JSON. The browser attaches the session
        // cookie to a cross-site POST regardless of what the response content type is.
        mockMvc.perform(post("/api/medications")
                        .with(Auth.as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Keppra 500mg","lastFilledOn":"2026-09-01",
                                 "daysSupply":30}
                                """))
                .andExpect(status().isForbidden());
    }
}
