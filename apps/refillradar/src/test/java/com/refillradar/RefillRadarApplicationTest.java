package com.refillradar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

import com.refillradar.domain.Account;
import com.refillradar.shortage.ShortageSource;
import com.refillradar.store.AccountRepository;
import com.refillradar.support.Auth;
import com.refillradar.support.DatabaseCleaner;
import com.refillradar.support.TestAccounts;

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

    @Autowired
    private AccountRepository accounts;

    private Account robert;

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
        // An account has to exist before anything can belong to it - medications.user_id
        // has been a foreign key since V2.
        robert = accounts.save(TestAccounts.user("robert"));
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
        // No userId in the payload - there is nowhere to put one. The owner comes from
        // the authenticated session instead.
        String payload = """
                {
                  "displayName": "Adderall XR 10mg",
                  "searchTerm": "Adderall",
                  "lastFilledOn": "2026-09-01",
                  "daysSupply": 30
                }
                """;

        mockMvc.perform(post("/api/medications")
                        .with(Auth.as(robert)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Adderall XR 10mg"))
                .andExpect(jsonPath("$.userId").value(robert.id()));

        mockMvc.perform(get("/api/me/supply-check").with(Auth.as(robert)))
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
        Account nobody = accounts.save(TestAccounts.user("nobody"));

        mockMvc.perform(get("/api/me/supply-check").with(Auth.as(nobody)))
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
                  "displayName": "Test",
                  "lastFilledOn": "2026-09-01",
                  "daysSupply": -5
                }
                """;

        mockMvc.perform(post("/api/medications")
                        .with(Auth.as(robert)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the normalisation endpoint explains how a name was interpreted")
    void normalizationEndpointIsDiagnostic() throws Exception {
        mockMvc.perform(get("/api/debug/normalize").param("name", "Adderall XR 10mg")
                        .with(Auth.as(robert)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recognisedBrand").value(true))
                .andExpect(jsonPath("$.tokens").isArray());
    }
}
