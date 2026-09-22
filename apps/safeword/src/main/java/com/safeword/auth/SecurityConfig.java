package com.safeword.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Who may reach what.
 *
 * <h2>Sessions, not JWTs</h2>
 * The fashionable choice for an API is a stateless JWT. It is the wrong one here, for a
 * reason that matters more here than usual: <b>a JWT cannot be revoked before it expires.</b>
 * The people this app protects are the people most likely to have a phone taken, borrowed or
 * talked out of their hands, so "log me out everywhere" has to take effect now. The usual
 * answer - a server-side denylist of revoked tokens - reintroduces exactly the shared state
 * that made JWTs attractive, while keeping their downsides.
 *
 * <p>A session id is a random opaque string whose meaning lives in one row of a table. Revoke
 * it by deleting the row. v0.2 puts PostgreSQL there anyway, so this costs nothing new, and
 * Spring Session JDBC means sessions survive a restart rather than logging everyone out on
 * every deploy.
 *
 * <h2>CSRF stays on</h2>
 * It is tempting to disable CSRF because "this is a JSON API". That reasoning is wrong: the
 * browser attaches the session cookie to a cross-site request whether or not the response is
 * JSON. {@code SameSite=Strict} (set in {@code application.yml}) blocks most of it, but it is
 * a second line rather than the only one - so the token check stays, and the token is served
 * in a readable cookie so a browser client can echo it back.
 */
@Configuration
@ConditionalOnProperty(name = "safeword.security.enabled", havingValue = "true",
        matchIfMissing = true)
public class SecurityConfig {

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

    /**
     * The filter chain: what is public, what needs a login, what needs ADMIN.
     *
     * @param http the builder Spring Security provides
     * @return the configured chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Hands the raw token to the cookie rather than deferring it, so the XSRF-TOKEN
        // cookie is actually populated on the first request a client makes.
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                    // withHttpOnlyFalse: a browser client has to READ this one to echo it
                    // back in a header, so unlike the session cookie it cannot be HttpOnly.
                    // That is safe because the token is not a credential on its own - it
                    // proves the request came from a page that could read same-origin state.
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfHandler)
                    // Registration and login are the two requests a client makes before it
                    // holds any session, and both are safe to allow without a token: neither
                    // acts on an existing account, so there is no authority to ride on.
                    //
                    // /api/check-call is here for the same reason and one more. It reads and
                    // writes nothing - the same tactics always score the same - so there is
                    // no authority for a cross-site request to borrow. Requiring a token
                    // would mean a client must first fetch one before it can ask "is this
                    // call a scam?", which is friction bought with no security at the only
                    // moment this app matters.
                    .ignoringRequestMatchers("/api/auth/register", "/api/auth/login",
                            "/api/check-call"))

            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()

                    // DELIBERATELY PUBLIC, and this is a product decision rather than an
                    // oversight. These four are what someone uses DURING a suspicious call:
                    // the pause screen, the tactic checker, the pattern library and the
                    // instructions for agreeing a passphrase. None of them reads or writes
                    // anyone's data - they are the same for every caller - and putting a
                    // login in front of them would mean an older adult being pressured by a
                    // stranger on the phone has to remember a password first. The login
                    // wall would protect nothing and cost exactly the moment the app exists
                    // for.
                    .requestMatchers("/api/pause", "/api/check-call", "/api/scam-patterns",
                            "/api/passphrase/instructions").permitAll()

                    // Everything under /api/me is somebody's circle. Default-deny covers it
                    // and anything added later, rather than leaving new routes open until
                    // somebody remembers to close them.
                    .anyRequest().authenticated())

            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    // A new session id is issued on login, so a fixed id planted before
                    // login cannot be used afterwards.
                    .sessionFixation(fixation -> fixation.changeSessionId()))

            // Without this, EVERY anonymous request to a protected route creates a session
            // row. Spring Security's default RequestCache stashes the rejected request in a
            // session so it can replay it after a form login - useful for a web app with a
            // login page, useless for a JSON API that answers 401 and expects the client to
            // call /api/auth/login itself.
            //
            // Measured before the fix: 20 unauthenticated GETs produced 20 rows in
            // SPRING_SESSION. That is an unauthenticated way to grow the database, which
            // makes it a denial-of-service vector rather than merely untidy.
            .requestCache(RequestCacheConfigurer::disable)

            .exceptionHandling(exceptions -> exceptions
                    // Without this Spring Security redirects to a login PAGE, so an
                    // unauthenticated API call gets a 302 and an HTML body. A client cannot
                    // tell that from success without inspecting the payload.
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .logout(logout -> logout
                    .logoutUrl("/api/auth/logout")
                    // No deleteCookies("JSESSIONID") here. Spring Session renames the cookie
                    // to SESSION and expires it itself on logout - verified against a live
                    // server. Naming JSESSIONID would emit a Set-Cookie for a cookie this
                    // application never sets, which looks like protection and is not.
                    .invalidateHttpSession(true)
                    .logoutSuccessHandler((request, response, authentication) ->
                            response.setStatus(HttpStatus.NO_CONTENT.value())))

            // No HTTP Basic and no form login: both would add a second way in that nothing
            // tests and nobody audits.
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }
}
