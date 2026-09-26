package com.renewalguard.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the response-window assumption.
 *
 * <p>Several assertions here check <b>honesty</b> rather than arithmetic: that an assumed
 * window admits it is assumed. Encoding that as a test means a future change which quietly
 * drops the caveat fails the build.
 */
class ResponseWindowTest {

    private final ResponseWindow window = new ResponseWindow();

    @Test
    @DisplayName("assumes the SHORTEST reported window when the user has not told us")
    void assumesShortestWindow() {
        // The asymmetry is the argument: assuming 30 days when someone has 10 loses their
        // coverage; assuming 10 when they have 30 just means they finish early.
        assertThat(window.planningWindowDays(null))
                .isEqualTo(ResponseWindow.CONSERVATIVE_DEFAULT_DAYS);
        assertThat(window.isAssumed(null)).isTrue();
    }

    @Test
    @DisplayName("uses the user's own figure when they supply one")
    void usesUserSuppliedWindow() {
        assertThat(window.planningWindowDays(30)).isEqualTo(30);
        assertThat(window.isAssumed(30)).isFalse();
    }

    @Test
    @DisplayName("nonsense input falls back to the conservative default")
    void nonsenseFallsBack() {
        assertThat(window.planningWindowDays(0))
                .isEqualTo(ResponseWindow.CONSERVATIVE_DEFAULT_DAYS);
        assertThat(window.planningWindowDays(-5))
                .isEqualTo(ResponseWindow.CONSERVATIVE_DEFAULT_DAYS);
    }

    @Test
    @DisplayName("an assumed window says so, and asks the user to check their notice")
    void assumedWindowIsHonest() {
        String explanation = window.explain(null);

        assertThat(explanation).contains("We assume");
        assertThat(explanation).contains("Check it");
    }

    @Test
    @DisplayName("a user-supplied window is described as theirs, not ours")
    void suppliedWindowIsAttributed() {
        assertThat(window.explain(21)).contains("21-day").contains("you entered");
    }
}
