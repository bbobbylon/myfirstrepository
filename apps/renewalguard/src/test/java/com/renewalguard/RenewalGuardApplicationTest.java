package com.renewalguard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * End-to-end test of the wired application over HTTP.
 *
 * <p>No database, no network. Unit tests can all pass while the application fails to start;
 * this proves the pieces are connected.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RenewalGuardApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String trackCase(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("a user can track a case and get full renewal status")
    void trackCaseThenGetStatus() throws Exception {
        String due = LocalDate.now().plusDays(20).toString();
        String id = trackCase("""
                {"userId":"robert","program":"MEDICAID","stateCode":"ca",
                 "category":"EXPANSION_ADULT","renewalDueOn":"%s"}
                """.formatted(due));

        mockMvc.perform(get("/api/cases/" + id + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stateCode").value("CA"))   // normalised to upper case
                .andExpect(jsonPath("$.urgency").value("HIGH"))
                .andExpect(jsonPath("$.daysUntilDue").value(20))
                .andExpect(jsonPath("$.needsAddressCheck").value(true))
                .andExpect(jsonPath("$.documents", Matchers.not(Matchers.empty())))
                .andExpect(jsonPath("$.reminderMilestones", Matchers.hasItem(30)));
    }

    @Test
    @DisplayName("status always carries the eligibility and notice caveats")
    void statusCarriesCaveats() throws Exception {
        String due = LocalDate.now().plusDays(45).toString();
        String id = trackCase("""
                {"userId":"robert","stateCode":"CA","category":"CHILD","renewalDueOn":"%s"}
                """.formatted(due));

        MvcResult result = mockMvc.perform(get("/api/cases/" + id + "/status"))
                .andExpect(status().isOk()).andReturn();

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
                {"userId":"robert","stateCode":"CA","category":"EXPANSION_ADULT",
                 "renewalDueOn":"2027-03-01"}
                """);

        mockMvc.perform(get("/api/cases/" + id + "/status"))
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
                {"userId":"robert","stateCode":"CA","category":"CHILD",
                 "renewalDueOn":"2027-03-01"}
                """);

        mockMvc.perform(get("/api/cases/" + id + "/status"))
                .andExpect(jsonPath("$.cadenceMonths").value(12))
                .andExpect(jsonPath("$.sixMonthChangeApplies").value(false))
                .andExpect(jsonPath("$.nextRenewalAfterThis").value("2028-03-01"));
    }

    @Test
    @DisplayName("an unspecified category says we assumed, rather than guessing silently")
    void unspecifiedCategorySaysSo() throws Exception {
        String id = trackCase("""
                {"userId":"robert","stateCode":"CA","renewalDueOn":"2027-03-01"}
                """);

        mockMvc.perform(get("/api/cases/" + id + "/status"))
                .andExpect(jsonPath("$.caveats", Matchers.hasItem(
                        Matchers.containsString("have not told us"))))
                .andExpect(jsonPath("$.cadenceMonths").value(12));
    }

    @Test
    @DisplayName("the reminder preview returns the real message a person would receive")
    void reminderPreviewReturnsRealCopy() throws Exception {
        String due = LocalDate.now().plusDays(5).toString();
        String id = trackCase("""
                {"userId":"robert","stateCode":"CA","category":"EXPANSION_ADULT",
                 "renewalDueOn":"%s"}
                """.formatted(due));

        MvcResult result = mockMvc.perform(get("/api/cases/" + id + "/reminder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wouldSendToday").value(true))
                .andReturn();

        String body = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("body").asText();

        assertThat(body).contains("never ask you for your Social Security number");
        assertThat(body).contains("Have you moved");
    }

    @Test
    @DisplayName("a case with a supplied response window uses it instead of assuming")
    void suppliedWindowIsUsed() throws Exception {
        String due = LocalDate.now().plusDays(60).toString();
        String id = trackCase("""
                {"userId":"robert","stateCode":"CA","category":"CHILD",
                 "renewalDueOn":"%s","responseWindowDays":30}
                """.formatted(due));

        mockMvc.perform(get("/api/cases/" + id + "/status"))
                .andExpect(jsonPath("$.windowExplanation",
                        Matchers.containsString("you entered")))
                .andExpect(jsonPath("$.documentsReadyBy")
                        .value(LocalDate.now().plusDays(30).toString()));
    }

    @Test
    @DisplayName("an unknown case is a 404, not an empty status")
    void unknownCaseIs404() throws Exception {
        mockMvc.perform(get("/api/cases/nope/status")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cases/nope/reminder")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("invalid input is rejected at the edge")
    void rejectsInvalidInput() throws Exception {
        // Missing the renewal date - the one field the whole app turns on.
        mockMvc.perform(post("/api/cases").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"robert","stateCode":"CA"}
                                """))
                .andExpect(status().isBadRequest());

        // A three-letter state code.
        mockMvc.perform(post("/api/cases").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"robert","stateCode":"CAL","renewalDueOn":"2027-01-01"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SNAP is accepted but says its rules are not modelled")
    void snapSaysItIsNotModelled() throws Exception {
        // Silently applying Medicaid's cadence to a SNAP case would produce a confident,
        // wrong deadline - worse than admitting the gap.
        String id = trackCase("""
                {"userId":"robert","program":"SNAP","stateCode":"CA",
                 "renewalDueOn":"2027-03-01"}
                """);

        mockMvc.perform(get("/api/cases/" + id + "/status"))
                .andExpect(jsonPath("$.caveats", Matchers.hasItem(
                        Matchers.containsString("not modelled yet"))));
    }
}
