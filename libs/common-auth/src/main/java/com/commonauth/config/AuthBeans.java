package com.commonauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The authentication beans every application using this module needs.
 */
@Configuration
public class AuthBeans {

    /**
     * How passwords are hashed and verified.
     *
     * <p>A <em>delegating</em> encoder: it writes hashes prefixed with the algorithm that
     * produced them, {@code {bcrypt}$2a$10$...}, and picks the verifier by reading that
     * prefix. The payoff is the migration you will eventually need - add argon2id as the
     * default later and new passwords use it while every existing hash still verifies. A
     * bare {@code BCryptPasswordEncoder} stores no such marker, so changing algorithm means
     * resetting everyone's password.
     *
     * @return the encoder, never {@code null}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Exposes the authentication manager so the login endpoint can call it directly.
     *
     * @param configuration Spring Security's assembled configuration
     * @return the authentication manager
     * @throws Exception if the manager cannot be built
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
