package com.commonauth.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.commonauth.store.LoginAttemptStore;

/**
 * Decides when someone has had enough tries at a password.
 *
 * <h2>Why two counters and not one</h2>
 * Per <b>username</b> alone misses password spraying: one popular password tried against ten
 * thousand accounts leaves a single failure on each, and every counter stays at one. Per
 * <b>IP</b> alone misses nothing in that case but is the wrong shape for a targeted attack
 * from many addresses, and it punishes an office or a mobile carrier sharing one address.
 * Each covers the other's blind spot, so an attempt must be under both limits to proceed.
 *
 * <h2>A window that heals, not a lockout</h2>
 * Locking an account until an administrator unlocks it turns "I know your username" into "I
 * can lock you out of your medication list", which is a denial of service handed to the
 * attacker for free. These counters simply age out: after the window, the account works
 * again with nobody being asked for anything.
 *
 * <h2>What this does not stop</h2>
 * A large botnet, spreading a few guesses per address across thousands of addresses, stays
 * under both limits. Rate limiting raises the cost of guessing; it does not make a weak
 * password safe. The defences that would help there - a proof of work, a CAPTCHA, a second
 * factor, telling the user someone is trying - are not built yet and are listed in the
 * README as missing rather than implied by this class.
 */
@Component
public class LoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    /**
     * How much of a username is used to build its key.
     *
     * <p>Login accepts any non-blank username, including a megabyte of it, and each failure
     * would otherwise be stored in full. Registration caps a username at 60 characters, so
     * truncating here cannot merge two real accounts into one bucket - only garbage with
     * garbage.
     */
    static final int MAX_KEY_VALUE_LENGTH = 60;

    static final String USERNAME_PREFIX = "user:";
    static final String IP_PREFIX = "ip:";
    static final String REGISTRATION_PREFIX = "reg:";

    private final LoginAttemptStore attempts;
    private final Clock clock;
    private final int maxFailuresPerUsername;
    private final int maxFailuresPerIp;
    private final Duration window;
    private final int maxRegistrationsPerIp;
    private final Duration registrationWindow;

    /**
     * @param attempts               where attempts are counted
     * @param clock                  the application clock, so windows are testable
     * @param maxFailuresPerUsername failures allowed against one username per window
     * @param maxFailuresPerIp       failures allowed from one address per window
     * @param window                 how long a failure counts for
     * @param maxRegistrationsPerIp  registration attempts allowed from one address
     * @param registrationWindow     how long a registration attempt counts for
     */
    public LoginThrottle(
            LoginAttemptStore attempts,
            Clock clock,
            @Value("${auth.login-throttle.max-failures-per-username:5}")
            int maxFailuresPerUsername,
            @Value("${auth.login-throttle.max-failures-per-ip:20}")
            int maxFailuresPerIp,
            @Value("${auth.login-throttle.window:PT15M}")
            Duration window,
            @Value("${auth.login-throttle.max-registrations-per-ip:5}")
            int maxRegistrationsPerIp,
            @Value("${auth.login-throttle.registration-window:PT1H}")
            Duration registrationWindow) {
        this.attempts = attempts;
        this.clock = clock;
        this.maxFailuresPerUsername = maxFailuresPerUsername;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.window = window;
        this.maxRegistrationsPerIp = maxRegistrationsPerIp;
        this.registrationWindow = registrationWindow;
    }

    /**
     * Whether this login attempt may be tried at all.
     *
     * <p>Counted identically whether or not the username exists. Skipping unknown usernames
     * would make the 429 answer the one question login is careful not to answer.
     *
     * @param username  the username as typed
     * @param clientIp  the address the request came from
     * @return an allow, or a refusal carrying when to come back
     */
    public Decision checkLogin(String username, String clientIp) {
        Instant now = Instant.now(clock);
        Instant since = now.minus(window);

        Decision byUsername = evaluate(usernameKey(username), maxFailuresPerUsername,
                window, since, now);
        if (!byUsername.allowed()) {
            return byUsername;
        }
        return evaluate(addressKey(IP_PREFIX, clientIp), maxFailuresPerIp, window, since, now);
    }

    /**
     * Records a failed login against both the username and the address.
     *
     * <p>Called only for attempts that were actually tried. A refused attempt writes nothing:
     * it keeps an attacker from growing the table without bound, and it keeps them from
     * extending someone else's block by continuing to hammer it.
     *
     * @param username the username that was tried
     * @param clientIp the address it came from
     */
    public void recordLoginFailure(String username, String clientIp) {
        Instant now = Instant.now(clock);
        attempts.record(usernameKey(username), now);
        attempts.record(addressKey(IP_PREFIX, clientIp), now);
    }

    /**
     * Clears the username's counter after a correct password.
     *
     * <p>The address counter is deliberately left alone: an attacker who owns one valid
     * account could otherwise reset their own address budget between bursts by logging into
     * it.
     *
     * @param username the username that just logged in
     */
    public void recordLoginSuccess(String username) {
        attempts.clear(usernameKey(username));
    }

    /**
     * Whether this address may try to register again.
     *
     * @param clientIp the address the request came from
     * @return an allow, or a refusal carrying when to come back
     */
    public Decision checkRegistration(String clientIp) {
        Instant now = Instant.now(clock);
        Instant since = now.minus(registrationWindow);
        return evaluate(addressKey(REGISTRATION_PREFIX, clientIp), maxRegistrationsPerIp,
                registrationWindow, since, now);
    }

    /**
     * Records a registration attempt.
     *
     * <p>Every attempt counts, not just the ones that create an account. Registration has to
     * say whether a username is taken, so unlimited attempts are an unlimited way to ask who
     * has an account here - and unlimited successes are a way to fill the users table.
     *
     * @param clientIp the address the request came from
     */
    public void recordRegistration(String clientIp) {
        attempts.record(addressKey(REGISTRATION_PREFIX, clientIp), Instant.now(clock));
    }

    /**
     * Deletes attempts too old to affect any decision.
     *
     * <p>The cutoff is the longer of the two windows, because one purge serves both. Counts
     * are already windowed, so this is housekeeping rather than correctness - but the table's
     * size is chosen by whoever is attacking, and unbounded growth is its own outage.
     *
     * <p>The initial delay is long enough that this never fires during a test run.
     */
    @Scheduled(initialDelayString = "${auth.login-throttle.purge-initial-delay:PT15M}",
               fixedDelayString = "${auth.login-throttle.purge-interval:PT15M}")
    public void purgeExpiredAttempts() {
        Duration longest = window.compareTo(registrationWindow) >= 0 ? window : registrationWindow;
        int removed = attempts.purgeOlderThan(Instant.now(clock).minus(longest));
        if (removed > 0) {
            log.debug("Purged {} expired login attempts", removed);
        }
    }

    private Decision evaluate(String key, int limit, Duration keyWindow, Instant since,
                              Instant now) {
        if (attempts.countSince(key, since) < limit) {
            return Decision.allow();
        }
        // The block lifts when the oldest attempt in the window ages out, so Retry-After is
        // a fact rather than a round number the client has to poll around.
        long retryAfter = attempts.earliestSince(key, since)
                .map(earliest -> secondsBetween(now, earliest.plus(keyWindow)))
                .orElse(1L);
        return Decision.refuse(retryAfter);
    }

    private static long secondsBetween(Instant from, Instant to) {
        // Rounded up, and never zero: "retry after 0 seconds" invites the immediate retry
        // this is trying to prevent.
        long millis = Duration.between(from, to).toMillis();
        return Math.max(1L, (millis + 999L) / 1000L);
    }

    private static String usernameKey(String username) {
        // Lowercased because the accounts table is unique on lower(username) - keying on the
        // raw text would give "Alice" and "alice" a separate budget each for one account.
        return USERNAME_PREFIX + truncate(
                username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String addressKey(String prefix, String clientIp) {
        return prefix + truncate(clientIp == null ? "unknown" : clientIp);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_KEY_VALUE_LENGTH
                ? value
                : value.substring(0, MAX_KEY_VALUE_LENGTH);
    }

    /**
     * The answer to "may this attempt proceed?".
     *
     * @param allowed           whether to try the credentials at all
     * @param retryAfterSeconds when to come back, meaningful only when refused
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {

        /**
         * @return a decision that lets the attempt through
         */
        public static Decision allow() {
            return new Decision(true, 0L);
        }

        /**
         * @param retryAfterSeconds when the block lifts
         * @return a decision that refuses the attempt
         */
        public static Decision refuse(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }
}
