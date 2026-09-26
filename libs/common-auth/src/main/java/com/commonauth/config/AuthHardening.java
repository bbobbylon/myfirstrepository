package com.commonauth.config;

import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * The parts of the security configuration that are the same in every application here.
 *
 * <p><b>What is deliberately NOT in this class: which routes are public.</b> That is a
 * product decision, not a security default - SafeWord leaves its pause screen and call
 * checker open to anyone on purpose, because someone being pressured by a stranger on the
 * phone must not meet a login wall. Each application supplies its own
 * {@code authorizeHttpRequests} rules and its own CSRF exemptions; only the hardening is
 * shared, so a route can never become public by accident in a file nobody read.
 *
 * <h2>Sessions, not JWTs</h2>
 * A JWT cannot be revoked before it expires, and "log me out everywhere" or "my phone was
 * taken" has to take effect now. The usual answer - a server-side denylist of revoked
 * tokens - reintroduces exactly the shared state that made JWTs attractive while keeping
 * their downsides. A session id is a random opaque string whose meaning lives in one row of
 * a table, so revoking it is a {@code DELETE}. Spring Session JDBC also means a deploy no
 * longer logs everyone out.
 *
 * <h2>CSRF stays on</h2>
 * It is tempting to disable CSRF because "this is a JSON API". That reasoning is wrong: the
 * browser attaches the session cookie to a cross-site request whether or not the response is
 * JSON. {@code SameSite=Strict} is the first line and this token check is the second.
 */
public final class AuthHardening {

    private AuthHardening() {
    }

    /**
     * Applies the shared hardening to a filter chain under construction.
     *
     * <p>The caller still has to add its own {@code authorizeHttpRequests} rules. Nothing
     * here decides who may reach what.
     *
     * @param http              the chain builder
     * @param csrfExemptPaths   paths that may be posted to without a CSRF token. Registration
     *                          and login belong here in every app - a client makes them
     *                          before it holds any session, so there is no authority for a
     *                          cross-site request to ride on. Anything else added here needs
     *                          the same argument made explicitly.
     * @throws Exception if the chain cannot be configured
     */
    public static void applyTo(HttpSecurity http, String... csrfExemptPaths) throws Exception {
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
                    .ignoringRequestMatchers(csrfExemptPaths))

            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    // A new session id is issued on login, so a fixed id planted before
                    // login cannot be used afterwards.
                    .sessionFixation(fixation -> fixation.changeSessionId()))

            // Without this, EVERY anonymous request to a protected route creates a session
            // row. Spring Security's default RequestCache stashes the rejected request in a
            // session so it can replay it after a form login - useful for a web app with a
            // login page, useless for a JSON API that answers 401 and expects the client to
            // call the login endpoint itself.
            //
            // Measured before the fix in RefillRadar: 20 unauthenticated GETs produced 20
            // rows in SPRING_SESSION. That is an unauthenticated way to grow the database,
            // which makes it a denial-of-service vector rather than merely untidy. Both
            // applications assert the zero-row property in their CI smoke tests.
            .requestCache(RequestCacheConfigurer::disable)

            .exceptionHandling(exceptions -> exceptions
                    // Without this Spring Security redirects to a login PAGE, so an
                    // unauthenticated API call gets a 302 and an HTML body. A client cannot
                    // tell that from success without inspecting the payload.
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .logout(logout -> logout
                    .logoutUrl("/api/auth/logout")
                    // No deleteCookies("JSESSIONID"). Spring Session renames the cookie to
                    // SESSION and expires it itself on logout - verified against a live
                    // server. Naming JSESSIONID would emit a Set-Cookie for a cookie these
                    // applications never set, which looks like protection and is not.
                    .invalidateHttpSession(true)
                    .logoutSuccessHandler((request, response, authentication) ->
                            response.setStatus(HttpStatus.NO_CONTENT.value())))

            // No HTTP Basic and no form login: both would add a second way in that nothing
            // tests and nobody audits.
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());
    }
}
