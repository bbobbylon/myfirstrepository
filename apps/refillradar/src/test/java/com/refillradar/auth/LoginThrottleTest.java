package com.refillradar.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.refillradar.store.InMemoryLoginAttemptStore;
import com.refillradar.store.LoginAttemptStore;

/**
 * The throttle's policy, at times of the test's choosing.
 *
 * <p>Every test here shares <b>one store</b> across throttles built on different fixed
 * clocks. That is what lets "the block lifts fifteen minutes later" be asserted in
 * milliseconds - and it is deliberate: an earlier ledger test in this project gave each
 * clock its own store, which made the window assertion vacuous. It would have passed with
 * the windowing deleted.
 */
class LoginThrottleTest {

    private static final Instant NOON = Instant.parse("2026-09-22T12:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);

    private static final int MAX_PER_USERNAME = 5;
    /** Small on purpose, so spraying can be shown in a handful of calls. */
    private static final int MAX_PER_IP = 6;
    private static final int MAX_REGISTRATIONS = 3;

    private static final String ATTACKER = "198.51.100.7";

    private LoginAttemptStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryLoginAttemptStore();
    }

    private LoginThrottle at(Instant when) {
        return new LoginThrottle(store, Clock.fixed(when, ZoneOffset.UTC),
                MAX_PER_USERNAME, MAX_PER_IP, WINDOW, MAX_REGISTRATIONS, REGISTRATION_WINDOW);
    }

    private void failTimes(LoginThrottle throttle, int times, String username, String ip) {
        for (int i = 0; i < times; i++) {
            throttle.recordLoginFailure(username, ip);
        }
    }

    @Test
    @DisplayName("attempts below the limit are allowed")
    void allowsAttemptsBelowTheLimit() {
        LoginThrottle throttle = at(NOON);
        failTimes(throttle, MAX_PER_USERNAME - 1, "alice", ATTACKER);

        assertThat(throttle.checkLogin("alice", ATTACKER).allowed())
                .as("a legitimate user who mistypes four times must still get a fifth go")
                .isTrue();
    }

    @Test
    @DisplayName("ATTACK: the sixth guess at one username is refused")
    void refusesOnceTheLimitIsReached() {
        LoginThrottle throttle = at(NOON);
        failTimes(throttle, MAX_PER_USERNAME, "alice", ATTACKER);

        LoginThrottle.Decision decision = throttle.checkLogin("alice", ATTACKER);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isPositive();
    }

    @Test
    @DisplayName("the block lifts exactly when the oldest failure leaves the window")
    void theBlockLiftsWhenTheWindowPasses() {
        failTimes(at(NOON), MAX_PER_USERNAME, "alice", ATTACKER);

        assertThat(at(NOON.plus(WINDOW).minusSeconds(1)).checkLogin("alice", ATTACKER).allowed())
                .as("still blocked one second inside the window")
                .isFalse();

        assertThat(at(NOON.plus(WINDOW)).checkLogin("alice", ATTACKER).allowed())
                .as("and unblocked by itself, with nobody asked to unlock anything")
                .isTrue();
    }

    @Test
    @DisplayName("Retry-After counts from the oldest failure, not from now")
    void retryAfterIsWhenTheBlockActuallyLifts() {
        failTimes(at(NOON), MAX_PER_USERNAME, "alice", ATTACKER);

        assertThat(at(NOON.plusSeconds(300)).checkLogin("alice", ATTACKER).retryAfterSeconds())
                .as("five minutes into a fifteen-minute window, ten minutes remain")
                .isEqualTo(600L);
    }

    @Test
    @DisplayName("a correct password clears the username's failures")
    void successClearsTheUsernameCounter() {
        LoginThrottle throttle = at(NOON);
        failTimes(throttle, MAX_PER_USERNAME, "alice", ATTACKER);

        throttle.recordLoginSuccess("alice");

        assertThat(throttle.checkLogin("alice", ATTACKER).allowed()).isTrue();
    }

    @Test
    @DisplayName("ATTACK: a correct password does NOT clear the address's failures")
    void successDoesNotClearTheAddressCounter() {
        // Otherwise an attacker who holds one valid account of their own resets their
        // address budget between bursts simply by logging into it.
        LoginThrottle throttle = at(NOON);
        failTimes(throttle, MAX_PER_IP, "alice", ATTACKER);

        throttle.recordLoginSuccess("alice");

        assertThat(throttle.checkLogin("bob", ATTACKER).allowed())
                .as("the address is still spent even though one username was cleared")
                .isFalse();
    }

    @Test
    @DisplayName("ATTACK: spraying one password across many usernames trips the address limit")
    void sprayingTripsTheAddressLimit() {
        // The attack the per-username counter cannot see: every username has exactly one
        // failure against it, which is indistinguishable from a typo.
        LoginThrottle throttle = at(NOON);
        for (int i = 0; i < MAX_PER_IP; i++) {
            assertThat(throttle.checkLogin("victim" + i, ATTACKER).allowed()).isTrue();
            throttle.recordLoginFailure("victim" + i, ATTACKER);
        }

        assertThat(throttle.checkLogin("victim-next", ATTACKER).allowed()).isFalse();
    }

    @Test
    @DisplayName("ATTACK: changing the case of a username does not buy a fresh budget")
    void usernameCountingIgnoresCase() {
        LoginThrottle throttle = at(NOON);
        failTimes(throttle, MAX_PER_USERNAME, "alice", ATTACKER);

        // The accounts table is unique on lower(username), so "Alice" and "alice" are one
        // account. Counting them separately would multiply the allowance by the number of
        // ways to capitalise a name.
        assertThat(throttle.checkLogin("ALICE", ATTACKER).allowed()).isFalse();
        assertThat(throttle.checkLogin("Alice", ATTACKER).allowed()).isFalse();
    }

    @Test
    @DisplayName("a refused attempt is not recorded, so hammering cannot extend the block")
    void refusedAttemptsDoNotExtendTheBlock() {
        LoginThrottle throttle = at(NOON);
        failTimes(throttle, MAX_PER_USERNAME, "alice", ATTACKER);

        for (int i = 0; i < 50; i++) {
            throttle.checkLogin("alice", ATTACKER);
        }

        // Two properties in one count: the table cannot be grown by an attacker who is
        // already blocked, and a victim cannot be held out of their own account by someone
        // continuing to knock.
        assertThat(store.countSince(LoginThrottle.USERNAME_PREFIX + "alice",
                NOON.minus(WINDOW))).isEqualTo(MAX_PER_USERNAME);
        assertThat(at(NOON.plus(WINDOW)).checkLogin("alice", ATTACKER).allowed()).isTrue();
    }

    @Test
    @DisplayName("registration is metered separately from login")
    void registrationHasItsOwnBudget() {
        LoginThrottle throttle = at(NOON);
        failTimes(throttle, MAX_PER_IP, "alice", ATTACKER);

        assertThat(throttle.checkRegistration(ATTACKER).allowed())
                .as("failed logins must not stop someone from signing up")
                .isTrue();

        for (int i = 0; i < MAX_REGISTRATIONS; i++) {
            throttle.recordRegistration(ATTACKER);
        }

        assertThat(throttle.checkRegistration(ATTACKER).allowed()).isFalse();
    }

    @Test
    @DisplayName("an absent address still counts, under one shared key")
    void missingAddressesAreNotAFreePass() {
        // getRemoteAddr() can be null in odd container setups. Treating that as "no key" is
        // how a throttle quietly stops throttling.
        LoginThrottle throttle = at(NOON);
        for (int i = 0; i < MAX_PER_IP; i++) {
            throttle.recordLoginFailure("victim" + i, null);
        }

        assertThat(throttle.checkLogin("victim-next", null).allowed()).isFalse();
    }

    @Test
    @DisplayName("the purge removes attempts too old to matter, and nothing newer")
    void purgeKeepsWhatIsStillInWindow() {
        Instant twoHoursLater = NOON.plus(Duration.ofHours(2));
        failTimes(at(NOON), MAX_PER_USERNAME, "alice", ATTACKER);
        failTimes(at(twoHoursLater), 1, "bob", ATTACKER);

        // The cutoff is the LONGER of the two windows - an hour, the registration one - so
        // deleting by the login window alone would throw away registration counters that are
        // still in force. Alice's failures are two hours old and cannot affect any decision;
        // bob's are current.
        at(twoHoursLater).purgeExpiredAttempts();

        assertThat(store.countSince(LoginThrottle.USERNAME_PREFIX + "alice", Instant.EPOCH))
                .isZero();
        assertThat(store.countSince(LoginThrottle.USERNAME_PREFIX + "bob", Instant.EPOCH))
                .isEqualTo(1);
    }
}
