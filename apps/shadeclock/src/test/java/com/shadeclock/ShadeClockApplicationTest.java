package com.shadeclock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.commonauth.domain.Account;
import com.commonauth.store.AccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadeclock.forecast.ForecastSource;
import com.shadeclock.support.Auth;
import com.shadeclock.support.DatabaseCleaner;
import com.shadeclock.support.TestAccounts;

/**
 * End-to-end test of the wired application over HTTP.
 *
 * <p>Runs against the fixture forecast (the default), so it needs no network - but since v0.2 it
 * <b>does</b> need PostgreSQL, because storage is now real. That is the point: this is the test
 * that proves the pieces are actually connected, and "connected" now includes Flyway having
 * migrated and Hibernate having validated the entities against the schema. Unit tests can all
 * pass while the application fails to start.
 *
 * <p>Every crew route now needs a session, and every write needs a CSRF token. The four tests
 * that broke when authentication arrived are the same four rewritten below - worth noting that
 * {@code rejectsInvalidCoordinates} started returning 403 rather than 400, because the CSRF check
 * runs before bean validation. That ordering is correct (reject as cheaply as possible), and the
 * test now supplies a token so it still exercises what it claims to.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseCleaner.class)
class ShadeClockApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ForecastSource forecastSource;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private AccountRepository accounts;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Account supervisor;

    /**
     * Starts every test from an empty database with one supervisor to own the crews.
     *
     * <p>Not optional. These tests commit for real, so without the clean the second run of the
     * suite sees the first run's rows.
     */
    @BeforeEach
    void emptyTheDatabase() {
        databaseCleaner.clean();
        // A crew cannot exist without an owner - crews.owner_account_id is a foreign key.
        supervisor = accounts.save(TestAccounts.user("supervisor"));
    }

    /**
     * Creates a crew as the supervisor and returns its id.
     *
     * @param payload the JSON body
     * @return the new crew's id
     * @throws Exception if the request fails
     */
    private String createCrew(String payload) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/crews")
                        .with(Auth.as(supervisor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Test
    @DisplayName("the context starts and defaults to the non-live forecast source")
    void contextLoads() {
        assertThat(forecastSource).isNotNull();
        assertThat(forecastSource.describeSource()).contains("NOT LIVE");
    }

    @Test
    @DisplayName("a supervisor can create a crew and get a heat plan back")
    void createCrewThenGetSchedule() throws Exception {
        String payload = """
                {
                  "name": "Paving Crew",
                  "siteLabel": "Route 12 resurfacing",
                  "latitude": 34.0522,
                  "longitude": -118.2437,
                  "jurisdiction": "CA",
                  "workers": [
                    {"name": "Adapted Veteran", "heatWorkStartedOn": "2026-05-01"},
                    {"name": "New Starter",     "heatWorkStartedOn": "2026-07-14"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/crews")
                        .with(Auth.as(supervisor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Paving Crew"));

        String crewId = createCrew(payload);

        mockMvc.perform(get("/api/me/crews/" + crewId + "/schedule")
                        .param("date", "2026-07-15").with(Auth.as(supervisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.crewName").value("Paving Crew"))
                .andExpect(jsonPath("$.schedule.jurisdictionName").value("California"))
                // The fixture peaks above 100°F, so breaks must be scheduled.
                .andExpect(jsonPath("$.schedule.breaks", Matchers.not(Matchers.empty())))
                // The unadapted worker must be named, not merely counted.
                .andExpect(jsonPath("$.schedule.workersNeedingWatching[0].name")
                        .value("New Starter"))
                .andExpect(jsonPath("$.forecastSource").exists());
    }

    @Test
    @DisplayName("every schedule response carries its caveats")
    void scheduleCarriesCaveats() throws Exception {
        String payload = """
                {"name":"C","siteLabel":"S","latitude":34.0,"longitude":-118.0,
                 "jurisdiction":"CA","workers":[]}
                """;

        String crewId = createCrew(payload);

        MvcResult result = mockMvc.perform(
                        get("/api/me/crews/" + crewId + "/schedule")
                                .param("date", "2026-07-15").with(Auth.as(supervisor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.verificationCaveat").exists())
                .andReturn();

        JsonNode advisories = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("schedule").get("advisories");

        // The caveats travel with the plan rather than living in documentation nobody reads
        // at 2pm on a roof.
        assertThat(advisories.toString()).contains("UNVERIFIED").contains("full sunshine");
    }

    @Test
    @DisplayName("an unknown crew is a 404, not an empty plan")
    void unknownCrewIs404() throws Exception {
        mockMvc.perform(get("/api/me/crews/does-not-exist/schedule").with(Auth.as(supervisor)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("invalid coordinates are rejected at the edge")
    void rejectsInvalidCoordinates() throws Exception {
        String badPayload = """
                {"name":"C","siteLabel":"S","latitude":999.0,"longitude":-118.0,
                 "jurisdiction":"CA","workers":[]}
                """;

        // With the token supplied, this reaches bean validation and is a 400 again. Without it
        // the CSRF filter answers 403 first, which is the correct order but tests nothing here.
        mockMvc.perform(post("/api/crews")
                        .with(Auth.as(supervisor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(badPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the heat index endpoint reports its own uncertainty")
    void heatIndexEndpointReportsUncertainty() throws Exception {
        mockMvc.perform(get("/api/heat-index")
                        .param("temperatureF", "90").param("humidity", "70"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatIndexF").value(106))
                .andExpect(jsonPath("$.statedErrorF").value(1.3))
                .andExpect(jsonPath("$.band").value("DANGER"))
                .andExpect(jsonPath("$.limitation").exists());
    }

    @Test
    @DisplayName("known jurisdictions are listed")
    void listsJurisdictions() throws Exception {
        mockMvc.perform(get("/api/jurisdictions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasItems("CA", "US-BASELINE")));
    }
}
