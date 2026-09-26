package com.renewalguard.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.commonauth.domain.Account;
import com.commonauth.web.AccountPrincipal;

/**
 * Authenticates a MockMvc request as a given account.
 *
 * <p>Shortcuts the login round trip for tests whose subject is something other than logging
 * in. The real register/login/logout flow is exercised end to end by the container smoke test
 * in CI and by the shared module's own tests, so it is covered by something rather than
 * assumed everywhere.
 */
public final class Auth {

    private Auth() {
    }

    /**
     * Runs the request as this account.
     *
     * @param account the account to authenticate as
     * @return a post-processor to pass to {@code MockMvc.perform(...).with(...)}
     */
    public static RequestPostProcessor as(Account account) {
        return user(new AccountPrincipal(account));
    }
}
