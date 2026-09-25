package com.refillradar.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.commonauth.domain.Account;
import com.refillradar.shortage.ShortageFetchException;
import com.refillradar.shortage.ShortageSource;
import com.refillradar.support.Auth;
import com.refillradar.support.TestAccounts;

/**
 * Verifies that a failure to reach the FDA never renders as reassurance.
 *
 * <p>This is a regression test for a real incident during development: running against the
 * live openFDA endpoint from a network that blocks it produced an unhandled {@code 500}.
 * The underlying behaviour was right - it failed loudly rather than returning an empty list
 * that would read as "no shortages found" - but the response left a client guessing.
 *
 * <p>Replaces the real {@link ShortageSource} with a mock that always throws, which is a
 * failure mode you cannot reliably produce against a live API on demand.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShortageSourceExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortageSource shortageSource;

    private final Account robert = TestAccounts.user("robert");

    @Test
    @DisplayName("a failed fetch returns 503, not 500 and not an empty all-clear")
    void failedFetchReturns503() throws Exception {
        given(shortageSource.fetchCurrentShortages())
                .willThrow(new ShortageFetchException("openFDA unreachable"));

        mockMvc.perform(get("/api/me/supply-check").with(Auth.as(robert)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("shortage_data_unavailable"));
    }

    @Test
    @DisplayName("the error body explicitly says this is NOT an all-clear")
    void errorBodyRefusesToImplySafety() throws Exception {
        given(shortageSource.fetchCurrentShortages())
                .willThrow(new ShortageFetchException("openFDA unreachable"));

        // The single most important assertion in this class. An outage must never be
        // mistakable for good news - that is the failure this whole application exists
        // to prevent, so it must not be reintroduced by the error path.
        mockMvc.perform(get("/api/me/supply-check").with(Auth.as(robert)))
                .andExpect(jsonPath("$.important")
                        .value(Matchers.containsString("NOT an all-clear")))
                .andExpect(jsonPath("$.checkedAt").exists());
    }
}
