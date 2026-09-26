package com.renewalguard.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.commonauth.config.AuthHardening;

/**
 * Who may reach what in RenewalGuard.
 *
 * <p>Only the route rules; the hardening is shared - see {@link AuthHardening} for the CSRF
 * repository, the session-fixation policy, the disabled request cache and the 401 entry
 * point. This is the third app on that module, and the whole file is now about fifteen lines
 * of policy instead of a fourth copy of the same eighty.
 *
 * <p>RenewalGuard's rules are the strictest of the three, and that is a product decision
 * rather than an oversight. SafeWord leaves four routes open because someone being pressured
 * on the phone cannot be asked to log in first. RenewalGuard has no such moment: every
 * endpoint here is about one person's benefits case, there is nothing useful to serve a
 * stranger, and so nothing is public beyond registering and logging in.
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
        // Only the two credential endpoints are CSRF-exempt, and only because a caller with
        // no session yet has no token to send. Nothing else here is exempt: unlike SafeWord's
        // /api/check-call, every remaining route reads or writes somebody's data, so there is
        // always authority for a cross-site request to borrow.
        AuthHardening.applyTo(http, "/api/auth/register", "/api/auth/login");

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()

                // Default-deny. Every route under /api/me is somebody's benefits case, and a
                // default of "authenticated" covers routes added later too, rather than
                // leaving each new one open until somebody remembers to close it. That
                // ordering matters: the safe default costs nothing, and the unsafe one is
                // only discovered by whoever finds the open route first.
                .anyRequest().authenticated());

        return http.build();
    }
}
