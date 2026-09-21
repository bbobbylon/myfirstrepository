package com.refillradar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.refillradar.shortage.ShortageSource;
import com.refillradar.support.DatabaseCleaner;

/**
 * End-to-end test of the wired application over HTTP.
 *
 * <p>Runs against the fixture shortage source (the default), so it needs no network - but
 * since v0.3 it <b>does</b> need PostgreSQL, because storage is now real. That is the point:
 * this is the test that proves the pieces are actually connected, and "connected" now
 * includes Flyway having migrated and Hibernate having validated the entities against the
 * schema. Unit tests can all pass while the application fails to start.
 *
 * <p>See {@code README.md} for the one command that starts a local database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseCleaner.class)
class RefillRadarApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortageSource shortageSource;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    /**
     * Starts every test from an empty database.
     *
     * <p>Not optional. These tests commit for real, so without this the second run of the
     * suite sees the first run's rows and fails on a count assertion - which is exactly what
     * happened when this test was first pointed at PostgreSQL.
     */
    @BeforeEach
    void emptyTheDatabase() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("the application context starts and wires exactly one shortage source")
    void contextLoads() {
        assertThat(shortageSource).isNotNull();
        // Defaults to the fixture source, so a deployment that forgot to configure the
        // live endpoint serves obviously-fake data rather than nothing.
        assertThat(shortageSource.describeSource()).contains("NOT LIVE");
    }

    @Test
    @DisplayName("a user can add a medication and get a supply check back")
    void addMedicationThenCheckSupply() throws Exception {
        String payload = """
                {
                  "userId": "robert",
                  "displayName": "Adderall XR 10mg",
                  "searchTerm": "Adderall",
                  "lastFilledOn": "2026-09-01",
                  "daysSupply": 30
                }
                """;

        mockMvc.perform(post("/api/medications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Adderall XR 10mg"));

        mockMvc.perform(get("/api/users/robert/supply-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicationsChecked").value(1))
                .andExpect(jsonPath("$.shortagesScanned").value(8))
                // The fixture lists an amphetamine shortage, so this should match.
                .andExpect(jsonPath("$.matches[0].medication").value("Adderall XR 10mg"))
                .andExpect(jsonPath("$.matches[0].fdaRecord").value("Adderall"))
                // Matched on the BRAND token, not the generic one. The fixture record
                // carries proprietary_name "Adderall", so both sides normalise to
                // {adderall, amphetamine} and the matcher returns the first overlap it
                // finds. ShortageMatcherTest covers the other path, where the FDA record
                // has no brand name and the match can only happen via "amphetamine".
                .andExpect(jsonPath("$.matches[0].matchedOn").value("adderall"))
                .andExpect(jsonPath("$.matches[0].risk").value("HIGH"));
    }

    @Test
    @DisplayName("a check for a user with no medications still reports provenance")
    void emptyCheckIsStillInformative() throws Exception {
        // "No matches" must be distinguishable from "we know nothing" - see
        // SupplyCheckResponse for why that distinction matters.
        mockMvc.perform(get("/api/users/nobody/supply-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicationsChecked").value(0))
                .andExpect(jsonPath("$.shortagesScanned").value(8))
                .andExpect(jsonPath("$.source").exists())
                .andExpect(jsonPath("$.checkedAt").exists());
    }

    @Test
    @DisplayName("invalid input is rejected at the edge with a 400")
    void rejectsInvalidInput() throws Exception {
        String badPayload = """
                {
                  "userId": "robert",
                  "displayName": "Test",
                  "lastFilledOn": "2026-09-01",
                  "daysSupply": -5
                }
                """;

        mockMvc.perform(post("/api/medications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the normalisation endpoint explains how a name was interpreted")
    void normalizationEndpointIsDiagnostic() throws Exception {
        mockMvc.perform(get("/api/debug/normalize").param("name", "Adderall XR 10mg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recognisedBrand").value(true))
                .andExpect(jsonPath("$.tokens").isArray());
    }
}
