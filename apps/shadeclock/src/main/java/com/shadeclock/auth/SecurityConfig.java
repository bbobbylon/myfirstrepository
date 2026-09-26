package com.shadeclock.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.commonauth.config.AuthHardening;

/**
 * Who may reach what in ShadeClock.
 *
 * <p>Only the route rules; the hardening is shared - see {@link AuthHardening} for the CSRF
 * repository, the session-fixation policy, the disabled request cache and the 401 entry point.
 * This is the fourth app on that module, and the whole file is route policy rather than a fourth
 * copy of the same eighty lines.
 *
 * <p>ShadeClock's split is the most interesting of the four, because both failure directions are
 * real:
 *
 * <ul>
 *   <li><b>Under-locking</b> was the v0.1 bug, and it was not subtle. {@code GET /api/crews}
 *       returned every crew in the system to anyone who asked - names, absence dates, site
 *       coordinates - with no id to guess.</li>
 *   <li><b>Over-locking</b> is the quieter failure. The heat-index calculator and the
 *       jurisdiction list hold nobody's data: they are a formula and a list of rule sets, the
 *       same answer for every caller. Putting a login in front of them would mean a worker on a
 *       roof at 2pm cannot check whether conditions are dangerous without an account - which
 *       protects nothing and costs exactly the moment the app exists for. Two tests and a CI
 *       assertion keep them open, because nothing looks broken when an app is merely useless.</li>
 * </ul>
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
        // Only the two credential endpoints are CSRF-exempt, and only because a caller with no
        // session yet has no token to send. The public routes below are GETs, which CSRF
        // protection does not apply to in the first place - so unlike SafeWord's /api/check-call
        // there is nothing to exempt here.
        AuthHardening.applyTo(http, "/api/auth/register", "/api/auth/login");

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()

                // DELIBERATELY PUBLIC - see the class comment. A formula and a list of rule
                // sets, identical for every caller, naming nobody.
                .requestMatchers("/api/heat-index", "/api/jurisdictions").permitAll()

                // Everything else is somebody's roster. Default-deny covers routes added later
                // too, rather than leaving each new one open until somebody remembers to close
                // it - which is precisely how v0.1's listing endpoint came to be public.
                .anyRequest().authenticated());

        return http.build();
    }
}
