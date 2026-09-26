package com.commonauth.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Says out loud, on every boot, when the session cookie is not marked {@code Secure}.
 *
 * <p>The setting has to default to {@code false} or {@code http://localhost} cannot log in.
 * That leaves a real risk: a deployment where nobody sets {@code SESSION_COOKIE_SECURE=true}
 * sends its session cookie - the whole credential - over any accidental plain-HTTP request.
 *
 * <p>A comment in {@code application.yml} is read once, by whoever edits the file. A warning
 * in the startup log is read every time anyone looks at why the service restarted, which is
 * the moment it can still be fixed cheaply.
 */
@Component
public class InsecureCookieWarning {

    private static final Logger log = LoggerFactory.getLogger(InsecureCookieWarning.class);

    private final boolean secureCookies;

    /**
     * @param secureCookies whether the session cookie carries the {@code Secure} attribute
     */
    public InsecureCookieWarning(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    /**
     * Logs the warning once the application is up.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnIfCookiesAreNotSecure() {
        if (secureCookies) {
            log.info("Session cookies are marked Secure.");
            return;
        }
        log.warn("Session cookies are NOT marked Secure. This is fine on http://localhost "
                + "and NOT fine anywhere else - the session cookie is the credential, and "
                + "without Secure it can travel in plaintext. Set SESSION_COOKIE_SECURE=true "
                + "for any deployment served over HTTPS.");
    }
}
