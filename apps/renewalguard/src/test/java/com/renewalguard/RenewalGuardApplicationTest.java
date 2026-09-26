package com.renewalguard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.commonauth.domain.Account;
import com.commonauth.store.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renewalguard.support.Auth;
import com.renewalguard.support.DatabaseCleaner;
import com.renewalguard.support.TestAccounts;

/**
 * End-to-end test of the wired application over HTTP.
 *
 * <p>Since v0.2 this needs PostgreSQL, because storage is now real. That is the point: this is
 * the test that proves the pieces are actually connected, and "connected" now includes Flyway
 * having migrated and Hibernate having validated the entities against the schema. Unit tests
 * can all pass while the application fails to start. See {@code README.md} for the one command
 * that starts a local database.
 *
 * <p><b>The clock is fixed here, and it had to be.</b> Every case below used to be filed with
 * {@code LocalDate.now().plusDays(n)} and asserted against a band computed from the same
 * expression - which looks self-consistent and is not. The test's {@code LocalDate.now()} reads
 * the JVM's default zone while the application's projector reads its injected UTC clock, so on
 * a machine east of Greenwich the two could disagree by a day; and a run that crossed midnight
 * would compare dates taken on either side of it. RefillRadar learned this the expensive way,
 * where a hardcoded date drifted from HIGH into CRITICAL over three days and failed a refactor
 * that had nothing to do with dates. Pinning the clock removes both problems at once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({DatabaseCleaner.class, RenewalGuardApplicationTest.FixedClock.class})
class RenewalGuardApplicationTest {

    /** The day this test believes it is. Every expected date below is derived from it. */
    private static final Instant NOW = Instant.parse("2026-09-26T09:00:00Z");

    /** The same instant as a date, for building deadlines a fixed number of days out. */
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneOffset.UTC);

    /**
     * Replaces the application's clock for this test only.
     *
     * <p>{@code @Primary} because the application defines its own {@code Clock} bean; this one
     * wins wherever a single {@code Clock} is injected.
     */
    @TestConfiguration
    static class FixedClock {

        /**
         * @return a clock stopped on {@link #NOW}
         */
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private AccountRepository accounts;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Account robert;

    /**
     * Starts every test from an empty database with one account to own the cases.
     *
     * <p>Not optional. These tests commit for real, so without the clean the second run of the
     * suite sees the first run's rows.
     */
    @BeforeEach
    void emptyTheDatabase() {
        databaseCleaner.clean();
        // A case cannot exist without an owner - benefit_cases.user_id is a foreign key.
        robert = accounts.save(TestAccounts.user("robert"));
    }

    /**
     * Files a case as Robert and returns its id.
     *
     * <p>No {@code userId} in the payload: there is nowhere to put one since v0.2. The owner
     * comes from the authenticated session.
     *
     * @param body the JSON payload
     * @return the new case's id
     * @throws Exception if the request fails
     */
    private String trackCase(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cases")
                        .with(Auth.as(robert)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    /**
     * Reads one of Robert's cases.
     *
     * @param id   the case id
     * @param view {@code "status"} or {@code "reminder"}
     * @return the performed request's result actions
     * @throws Exception if the request fails
     */
    private org.springframework.test.web.servlet.ResultActions read(String id, String view)
            throws Exception {
        return mockMvc.perform(get("/api/me/cases/" + id + "/" + view).with(Auth.as(robert)));
    }

    @Test
    @DisplayName("a user can track a case and get full renewal status")
    void trackCaseThenGetStatus() throws Exception {
        String id = trackCase("""
                {"program":"MEDICAID","stateCode":"ca",
                 "category":"EXPANSION_ADULT","renewalDueOn":"%s"}
                """.formatted(TODAY.plusDays(20)));

        read(id, "status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stateCode").value("CA"))   // normalised to upper case
                // 20 days out bands as HIGH (CRITICAL is 10 or fewer). With the real clock
                // this assertion silently changed meaning as the suite crossed midnight.
                .andExpect(jsonPath("$.urgency").value("HIGH"))
                .andExpect(jsonPath("$.daysUntilDue").value(20))
                .andExpect(jsonPath("$.needsAddressCheck").value(true))
                .andExpect(jsonPath("$.documents", Matchers.not(Matchers.empty())))
                .andExpect(jsonPath("$.reminderMilestones", Matchers.hasItem(30)));
    }

    @Test
    @DisplayName("the stored case really round-trips through PostgreSQL")
    void caseRoundTripsThroughTheDatabase() throws Exception {
        String id = trackCase("""
                {"stateCode":"TX","category":"CHILD","renewalDueOn":"%s",
                 "noticeReceivedOn":"%s","addressConfirmedOn":"%s","responseWindowDays":21}
                """.formatted(TODAY.plusDays(40), TODAY.minusDays(3), TODAY.minusDays(10)));

        // Listed from the database, not from a map on the controller.
        mockMvc.perform(get("/api/me/cases").with(Auth.as(robert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].stateCode").value("TX"))
                // The field that used to live in a ConcurrentHashMap beside the repository,
                // and therefore vanished on restart while the case survived.
                .andExpect(jsonPath("$[0].responseWindowDays").value(21))
                .andExpect(jsonPath("$[0].addressConfirmedOn")
                        .value(TODAY.minusDays(10).toString()));

        // And it is used, rather than merely stored: 40 days out minus a 21-day window.
        read(id, "status")
                .andExpect(jsonPath("$.documentsReadyBy")
                        .value(TODAY.plusDays(19).toString()))
                .andExpect(jsonPath("$.windowExplanation",
                        Matchers.containsString("you entered")))
                // Confirmed 10 days ago, and the staleness threshold is 180 days.
                .andExpect(jsonPath("$.needsAddressCheck").value(false));
    }

    @Test
    @DisplayName("status always carries the eligibility and notice caveats")
    void statusCarriesCaveats() throws Exception {
        String id = trackCase("""
                {"stateCode":"CA","category":"CHILD","renewalDueOn":"%s"}
                """.formatted(TODAY.plusDays(45)));

        MvcResult result = read(id, "status").andExpect(status().isOk()).andReturn();

        String caveats = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("caveats").toString();

        // The two standing honesty rules travel with every response.
        assertThat(caveats).contains("does NOT decide whether");
        assertThat(caveats).contains("believe the notice");
    }

    @Test
    @DisplayName("an expansion adult is warned that renewals double from 2027")
    void expansionAdultIsWarnedAboutSixMonthChange() throws Exception {
        String id = trackCase("""
                {"stateCode":"CA","category":"EXPANSION_ADULT",
                 "renewalDueOn":"2027-03-01"}
                """);

        read(id, "status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cadenceMonths").value(6))
                .andExpect(jsonPath("$.sixMonthChangeApplies").value(true))
                .andExpect(jsonPath("$.nextRenewalAfterThis").value("2027-09-01"))
                .andExpect(jsonPath("$.caveats", Matchers.hasItem(
                        Matchers.containsString("SIX months"))));
    }

    @Test
    @DisplayName("a child on the same date stays annual and is not warned")
    void childStaysAnnual() throws Exception {
        String id = trackCase("""
                {"stateCode":"CA","category":"CHILD",
                 "renewalDueOn":"2027-03-01"}
                """);

        read(id, "status")
                .andExpect(jsonPath("$.cadenceMonths").value(12))
                .andExpect(jsonPath("$.sixMonthChangeApplies").value(false))
                .andExpect(jsonPath("$.nextRenewalAfterThis").value("2028-03-01"));
    }

    @Test
    @DisplayName("an unspecified category says we assumed, rather than guessing silently")
    void unspecifiedCategorySaysSo() throws Exception {
        String id = trackCase("""
                {"stateCode":"CA","renewalDueOn":"2027-03-01"}
                """);

        read(id, "status")
                .andExpect(jsonPath("$.caveats", Matchers.hasItem(
                        Matchers.containsString("have not told us"))))
                .andExpect(jsonPath("$.cadenceMonths").value(12));
    }

    @Test
    @DisplayName("the reminder preview returns the real message a person would receive")
    void reminderPreviewReturnsRealCopy() throws Exception {
        String id = trackCase("""
                {"stateCode":"CA","category":"EXPANSION_ADULT","renewalDueOn":"%s"}
                """.formatted(TODAY.plusDays(5)));

        MvcResult result = read(id, "reminder")
                .andExpect(status().isOk())
                // Inside 10 days the ladder reminds every day.
                .andExpect(jsonPath("$.wouldSendToday").value(true))
                .andReturn();

        String body = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("body").asText();

        assertThat(body).contains("never ask you for your Social Security number");
        assertThat(body).contains("Have you moved");
    }

    @Test
    @DisplayName("with no window supplied we assume the shortest, and say so")
    void unsuppliedWindowFallsBackConservatively() throws Exception {
        String id = trackCase("""
                {"stateCode":"CA","category":"CHILD","renewalDueOn":"%s"}
                """.formatted(TODAY.plusDays(60)));

        // 10 days is the conservative default: assuming a generous window and being wrong
        // means telling someone they have time they do not have.
        read(id, "status")
                .andExpect(jsonPath("$.windowExplanation",
                        Matchers.containsString("as little as 10")))
                .andExpect(jsonPath("$.documentsReadyBy")
                        .value(TODAY.plusDays(50).toString()));
    }

    @Test
    @DisplayName("an unknown case is a 404, not an empty status")
    void unknownCaseIs404() throws Exception {
        read("nope", "status").andExpect(status().isNotFound());
        read("nope", "reminder").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("invalid input is rejected at the edge")
    void rejectsInvalidInput() throws Exception {
        // Missing the renewal date - the one field the whole app turns on.
        mockMvc.perform(post("/api/cases").with(Auth.as(robert)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stateCode":"CA"}
                                """))
                .andExpect(status().isBadRequest());

        // A three-letter state code.
        mockMvc.perform(post("/api/cases").with(Auth.as(robert)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stateCode":"CAL","renewalDueOn":"2027-01-01"}
                                """))
                .andExpect(status().isBadRequest());

        // A zero response window, which would put the preparation deadline on or after the
        // date it is meant to precede.
        mockMvc.perform(post("/api/cases").with(Auth.as(robert)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stateCode":"CA","renewalDueOn":"2027-01-01",
                                 "responseWindowDays":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SNAP is accepted but says its rules are not modelled")
    void snapSaysItIsNotModelled() throws Exception {
        // Silently applying Medicaid's cadence to a SNAP case would produce a confident,
        // wrong deadline - worse than admitting the gap.
        String id = trackCase("""
                {"program":"SNAP","stateCode":"CA","renewalDueOn":"2027-03-01"}
                """);

        read(id, "status")
                .andExpect(jsonPath("$.caveats", Matchers.hasItem(
                        Matchers.containsString("not modelled yet"))));
    }

    @Test
    @DisplayName("a user can stop tracking their own case")
    void ownerCanDeleteTheirOwnCase() throws Exception {
        String id = trackCase("""
                {"stateCode":"CA","renewalDueOn":"%s"}
                """.formatted(TODAY.plusDays(30)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/me/cases/" + id).with(Auth.as(robert)).with(csrf()))
                .andExpect(status().isNoContent());

        // Gone from the database, not just from a response.
        read(id, "status").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/cases").with(Auth.as(robert)))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
