package com.shadeclock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shadeclock.forecast.ForecastSource;

/**
 * End-to-end test of the wired application over HTTP.
 *
 * <p>Runs against the fixture forecast (the default), so the whole suite still needs no
 * network and no database. Unit tests can all pass while the application fails to start;
 * this is the test that proves the pieces are connected to each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShadeClockApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ForecastSource forecastSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        MvcResult created = mockMvc.perform(post("/api/crews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Paving Crew"))
                .andReturn();

        String crewId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/crews/" + crewId + "/schedule").param("date", "2026-07-15"))
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

        MvcResult created = mockMvc.perform(post("/api/crews")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        String crewId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult result = mockMvc.perform(
                        get("/api/crews/" + crewId + "/schedule").param("date", "2026-07-15"))
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
        mockMvc.perform(get("/api/crews/does-not-exist/schedule"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("invalid coordinates are rejected at the edge")
    void rejectsInvalidCoordinates() throws Exception {
        String badPayload = """
                {"name":"C","siteLabel":"S","latitude":999.0,"longitude":-118.0,
                 "jurisdiction":"CA","workers":[]}
                """;

        mockMvc.perform(post("/api/crews")
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
