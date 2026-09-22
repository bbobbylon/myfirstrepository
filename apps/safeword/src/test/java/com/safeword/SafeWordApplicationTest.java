package com.safeword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeword.domain.Account;
import com.safeword.store.AccountRepository;
import com.safeword.support.Auth;
import com.safeword.support.DatabaseCleaner;
import com.safeword.support.TestAccounts;

/**
 * End-to-end test of the wired application over HTTP.
 *
 * <p>From v0.2 the circle routes need a session, so each test runs as an account. The
 * public routes below are performed with no authentication on purpose - that they stay
 * reachable to an anonymous caller is a product requirement, not an accident of config.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseCleaner.class)
class SafeWordApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Account owner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        owner = accounts.save(TestAccounts.user("circle-owner"));
    }

    private String createCircle(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/me/circle")
                        .with(Auth.as(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("THE security invariant: the API refuses to accept a passphrase")
    void apiRefusesToAcceptAPassphrase() throws Exception {
        // The single most important test in SafeWord. If this ever passes a secret through,
        // the product's central promise is broken.
        mockMvc.perform(post("/api/me/circle")
                        .with(Auth.as(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"The Family","passphrase":"bluebird","members":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("passphrase_not_accepted"))
                .andExpect(jsonPath("$.message",
                        Matchers.containsString("never store it")));
    }

    @Test
    @DisplayName("a circle without a passphrase is reported as not yet protecting anyone")
    void circleWithoutPassphraseIsNotProtecting() throws Exception {
        String id = createCircle("""
                {"name":"The Family","members":[
                  {"name":"Ana","role":"RESPONDER","contact":"+15551234567"}]}
                """);

        mockMvc.perform(get("/api/me/circle/status").with(Auth.as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passphraseStatus").value("NOT_AGREED"))
                .andExpect(jsonPath("$.setupComplete").value(false))
                .andExpect(jsonPath("$.outstandingSteps", Matchers.hasItem(
                        Matchers.containsString("not protecting you"))));
    }

    @Test
    @DisplayName("a circle with a passphrase date and a responder is complete")
    void completeCircleIsReportedComplete() throws Exception {
        String id = createCircle("""
                {"name":"The Family","passphraseAgreedOn":"%s","members":[
                  {"name":"Dad","role":"PROTECTED_PERSON","contact":"+15550000000"},
                  {"name":"Ana","role":"RESPONDER","contact":"+15551234567"}]}
                """.formatted(java.time.LocalDate.now().minusDays(7)));

        mockMvc.perform(get("/api/me/circle/status").with(Auth.as(owner)))
                .andExpect(jsonPath("$.passphraseStatus").value("AGREED"))
                .andExpect(jsonPath("$.setupComplete").value(true))
                .andExpect(jsonPath("$.responderCount").value(1))
                .andExpect(jsonPath("$.outstandingSteps", Matchers.empty()));
    }

    @Test
    @DisplayName("the pause screen is short, absolute and reassuring")
    void pauseScreenIsUsable() throws Exception {
        mockMvc.perform(get("/api/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline", Matchers.containsString("Take your time")))
                .andExpect(jsonPath("$.items", Matchers.hasSize(Matchers.greaterThan(4))))
                .andExpect(jsonPath("$.items[0].statement",
                        Matchers.containsString("gift cards")));
    }

    @Test
    @DisplayName("a cloned-voice grandparent scam is scored STOP with the right pattern")
    void grandparentScamScoresStop() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/check-call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"signals":["FAMILY_EMERGENCY","SECRECY","URGENCY",
                                            "IRREVERSIBLE_PAYMENT"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("STOP"))
                .andExpect(jsonPath("$.standaloneRedFlag").value(true))
                .andExpect(jsonPath("$.likelyPatterns[0].name",
                        Matchers.containsString("Family emergency")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Never claims certainty, and points at the passphrase rather than voice analysis.
        assertThat(body).contains("not a certainty");
        assertThat(body).contains("PASSPHRASE");
    }

    @Test
    @DisplayName("an empty call check returns no flags rather than an error")
    void emptyCallCheckIsFine() throws Exception {
        mockMvc.perform(post("/api/check-call").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"signals":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("NO_FLAGS"))
                .andExpect(jsonPath("$.score").value(0));
    }

    @Test
    @DisplayName("escalation composes the message and is honest that it does not send it")
    void escalationIsHonestAboutDelivery() throws Exception {
        String id = createCircle("""
                {"name":"The Family","passphraseAgreedOn":"%s","members":[
                  {"name":"Mum","role":"PROTECTED_PERSON","contact":"+15550000000"},
                  {"name":"Ana","role":"RESPONDER","contact":"+15551234567"},
                  {"name":"Sam","role":"RESPONDER","contact":"+15559876543"}]}
                """.formatted(java.time.LocalDate.now().minusDays(7)));

        MvcResult result = mockMvc.perform(post("/api/me/circle/escalate")
                        .with(Auth.as(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"aboutMoney":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notified", Matchers.containsInAnyOrder("Ana", "Sam")))
                // A person who believes help is coming and is wrong is worse off than one
                // who knows it is not. v0.1 says so rather than implying delivery.
                .andExpect(jsonPath("$.actuallyDelivered").value(false))
                .andExpect(jsonPath("$.deliveryNote",
                        Matchers.containsString("does not send them")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("has asked for a call");
        assertThat(body).doesNotContain("scam");
    }

    @Test
    @DisplayName("passphrase instructions are served and insist on in-person agreement")
    void instructionsAreServed() throws Exception {
        mockMvc.perform(get("/api/passphrase/instructions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreement", Matchers.containsString("IN PERSON")))
                .andExpect(jsonPath("$.usage", Matchers.containsString("Do not say it first")))
                .andExpect(jsonPath("$.whyWeNeverStoreIt",
                        Matchers.containsString("never store it")));
    }

    @Test
    @DisplayName("the scam pattern library is served with its loss figures")
    void scamPatternsAreServed() throws Exception {
        mockMvc.perform(get("/api/scam-patterns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(5)))
                .andExpect(jsonPath("$[0].reportedLosses", Matchers.not(Matchers.emptyString())));
    }

    @Test
    @DisplayName("an account that has not set up a circle gets a 404, not an empty circle")
    void accountWithoutACircleIs404() throws Exception {
        // 404 rather than a hollow 200: "you have no circle" and "your circle is empty" are
        // different answers, and only one of them tells the user to go and set it up.
        mockMvc.perform(get("/api/me/circle/status").with(Auth.as(owner)))
                .andExpect(status().isNotFound());
    }
}
