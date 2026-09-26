package com.safeword.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.commonauth.config.AuthHardening;

/**
 * Who may reach what in SafeWord.
 *
 * <p>Only the route rules; the hardening is shared - see {@link AuthHardening}. That split
 * matters most here, because SafeWord's rules are the ones that differ: this app
 * deliberately leaves four routes open to anyone, and that decision should be visible in
 * the app rather than buried in a shared default.
 *
 * <p>There used to be a {@code @ConditionalOnProperty} here that could switch the whole
 * filter chain off. Nothing set it, so it was a documented way to disable authentication
 * that no test would have caught. Removed.
 */
@Configuration
public class SecurityConfig {

    /**
     * The filter chain: what is public, and what needs a session.
     *
     * @param http the builder Spring Security provides
     * @return the configured chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // /api/check-call joins registration and login as CSRF-exempt for a reason of its
        // own: it reads and writes nothing - the same tactics always score the same - so
        // there is no authority for a cross-site request to borrow, and demanding a token
        // would mean fetching one before you can ask "is this call a scam?".
        AuthHardening.applyTo(http, "/api/auth/register", "/api/auth/login", "/api/check-call");

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()

                // DELIBERATELY PUBLIC, and this is a product decision rather than an
                // oversight. These four are what someone uses DURING a suspicious call:
                // the pause screen, the tactic checker, the pattern library and the
                // instructions for agreeing a passphrase. None of them reads or writes
                // anyone's data - they are the same for every caller - and putting a login
                // in front of them would mean an older adult being pressured by a stranger
                // on the phone has to remember a password first. The login wall would
                // protect nothing and cost exactly the moment the app exists for.
                .requestMatchers("/api/pause", "/api/check-call", "/api/scam-patterns",
                        "/api/passphrase/instructions").permitAll()

                // Everything under /api/me is somebody's circle. Default-deny covers it
                // and anything added later, rather than leaving new routes open until
                // somebody remembers to close them.
                .anyRequest().authenticated());

        return http.build();
    }
}
