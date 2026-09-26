package com.refillradar.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.commonauth.config.AuthHardening;

/**
 * Who may reach what in RefillRadar.
 *
 * <p>This file is <em>only</em> the route rules. Everything else - CSRF, session fixation,
 * the request-cache fix, the 401 entry point, logout, password hashing - lives in
 * {@link AuthHardening} and {@code AuthBeans}, shared with the other applications here.
 * The split is deliberate: hardening is the same everywhere and a copy that drifts is a
 * vulnerability, while <b>which routes are public is a product decision</b> that has to be
 * read and argued per application.
 *
 * <p>There used to be a {@code @ConditionalOnProperty} here that could switch the whole
 * filter chain off. Nothing set it - no test, no config file, no deployment - so it was a
 * documented way to disable authentication that nothing would have caught. Removed.
 */
@Configuration
public class SecurityConfig {

    /**
     * The filter chain: what is public, what needs a login, what needs ADMIN.
     *
     * @param http the builder Spring Security provides
     * @return the configured chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Registration and login are the only requests a client makes before it holds a
        // session, so there is no authority for a cross-site request to ride on.
        AuthHardening.applyTo(http, "/api/auth/register", "/api/auth/login");

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/health").permitAll()
                // Triggering a sync hits the FDA and can send mail to every user. It was
                // open to anyone in v0.3, which made it a free way to generate load and
                // to spam a user base.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Default-deny. Anything added later is protected until someone
                // deliberately opens it, rather than open until someone remembers.
                .anyRequest().authenticated());

        return http.build();
    }
}
