package com.refillradar.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.refillradar.domain.Account;
import com.refillradar.domain.AccountRole;
import com.refillradar.store.AccountRepository;
import com.refillradar.support.DatabaseCleaner;

/**
 * The rate limiter over real HTTP, against the real database and the shipped limits.
 *
 * <p>Runs against the configured defaults rather than test-only values, so what is asserted
 * here is what a deployment actually does. That costs a few seconds of BCrypt and is worth
 * it: a throttle tested only at limits invented for the test proves the mechanism works and
 * says nothing about whether the numbers shipped are the numbers meant.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DatabaseCleaner.class)
class LoginThrottleIntegrationTest {

    private static final String PASSWORD = "a-long-enough-passphrase";
    private static final String WRONG = "not-the-password";

    /** Documentation-range addresses (RFC 5737), so no test ever names a real host. */
    private static final String ONE_ADDRESS = "198.51.100.10";
    private static final String ANOTHER_ADDRESS = "198.51.100.20";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
    }

    private Account accountNamed(String username) {
        return accounts.save(new Account(UUID.randomUUID().toString(), username,
                passwordEncoder.encode(PASSWORD), AccountRole.USER,
                Instant.parse("2026-01-01T00:00:00Z")));
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private ResultActions login(String username, String password, String address)
            throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(from(address))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                        .formatted(username, password)));
    }

    @Test
    @DisplayName("ATTACK: the sixth guess at one account is refused with 429 and Retry-After")
    void guessingIsRefusedAfterFiveWrongPasswords() throws Exception {
        accountNamed("alice");

        for (int attempt = 1; attempt <= 5; attempt++) {
            login("alice", WRONG, ONE_ADDRESS).andExpect(status().isUnauthorized());
        }

        String retryAfter = login("alice", WRONG, ONE_ADDRESS)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("too_many_attempts"))
                .andReturn().getResponse().getHeader("Retry-After");

        // Pins the CONFIGURED window, not just the mechanism. Without this, a window that
        // bound as fifteen seconds - or as the default of some property nobody set - would
        // pass every other test in this class.
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter))
                .as("the block should lift about fifteen minutes after the first failure")
                .isBetween(840L, 900L);
    }

    @Test
    @DisplayName("ATTACK: a blocked caller is refused even when the password is right")
    void theCredentialsAreNotEvenCheckedOnceBlocked() throws Exception {
        accountNamed("alice");

        for (int attempt = 1; attempt <= 5; attempt++) {
            login("alice", WRONG, ONE_ADDRESS).andExpect(status().isUnauthorized());
        }

        // The observable proof that the refusal happens before the credential check: the
        // correct password gets the same answer as a wrong one. That ordering is what keeps
        // an attacker from spending this server's CPU on BCrypt at will.
        login("alice", PASSWORD, ONE_ADDRESS).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("ATTACK: being throttled does not reveal whether the account exists")
    void theRefusalIsIdenticalForAnAccountThatDoesNotExist() throws Exception {
        accountNamed("alice");

        for (int attempt = 1; attempt <= 5; attempt++) {
            login("alice", WRONG, ONE_ADDRESS).andExpect(status().isUnauthorized());
            login("ghost", WRONG, ANOTHER_ADDRESS).andExpect(status().isUnauthorized());
        }

        String real = login("alice", WRONG, ONE_ADDRESS)
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        String imaginary = login("ghost", WRONG, ANOTHER_ADDRESS)
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();

        // Counting only real accounts would have made the 429 itself the answer to the
        // question login refuses to answer.
        assertThat(real).isEqualTo(imaginary);
    }

    @Test
    @DisplayName("a correct password clears the count, so mistyping does not accumulate")
    void aSuccessfulLoginResetsTheCounter() throws Exception {
        accountNamed("alice");

        for (int attempt = 1; attempt <= 4; attempt++) {
            login("alice", WRONG, ONE_ADDRESS).andExpect(status().isUnauthorized());
        }
        login("alice", PASSWORD, ONE_ADDRESS).andExpect(status().isOk());

        // Five more must all be answered, not blocked on the first: yesterday's typos are
        // not evidence of an attack today.
        for (int attempt = 1; attempt <= 5; attempt++) {
            login("alice", WRONG, ONE_ADDRESS).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("throttling one account does not lock anybody else out")
    void oneThrottledUsernameDoesNotBlockAnother() throws Exception {
        accountNamed("alice");
        accountNamed("bob");

        for (int attempt = 1; attempt <= 6; attempt++) {
            login("alice", WRONG, ONE_ADDRESS);
        }

        // Same address, different account: a per-username block that also blocked the
        // address would let anyone lock out an entire office by guessing at one colleague.
        login("bob", PASSWORD, ONE_ADDRESS).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ATTACK: spraying one password across many accounts trips the address limit")
    void sprayingAcrossUsernamesIsCaught() throws Exception {
        // Each username collects exactly one failure, so the per-username counter never
        // moves. This is the attack that makes the second counter necessary.
        for (int attempt = 1; attempt <= 20; attempt++) {
            login("victim" + attempt, WRONG, ONE_ADDRESS).andExpect(status().isUnauthorized());
        }

        login("victim-next", WRONG, ONE_ADDRESS).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("registration is rationed per address")
    void registrationIsRationedPerAddress() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            register("newcomer" + attempt, ONE_ADDRESS).andExpect(status().isCreated());
        }

        register("newcomer6", ONE_ADDRESS)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // A different address is unaffected - the meter is per source, not global, so one
        // busy office cannot stop the rest of the internet signing up.
        register("newcomer7", ANOTHER_ADDRESS).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("ATTACK: registration cannot be used to enumerate usernames without limit")
    void repeatedUsernameTakenAnswersAreRationedToo() throws Exception {
        accountNamed("alice");

        // Registration has to say whether a username is taken, which makes it an oracle.
        // Rationing every attempt - conflicts included - is what bounds how much of the user
        // list one address can read out of it.
        for (int attempt = 1; attempt <= 5; attempt++) {
            register("alice", ONE_ADDRESS).andExpect(status().isConflict());
        }

        register("alice", ONE_ADDRESS).andExpect(status().isTooManyRequests());
    }

    private ResultActions register(String username, String address) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .with(from(address))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                        .formatted(username, PASSWORD)));
    }
}
