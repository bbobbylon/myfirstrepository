package com.refillradar.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.refillradar.domain.Account;

/**
 * The logged-in account, as Spring Security carries it through a request.
 *
 * <p><b>This class is the fix for v0.3's worst bug.</b> Every endpoint used to take a
 * {@code userId} from the URL or the request body and trust it, so anyone could read, add to
 * or delete anyone else's medication list by supplying their id. The repair is not to check
 * the supplied id against the session - it is to stop accepting one. {@link #accountId()}
 * comes from the session cookie, which the client cannot forge, and there is no longer any
 * request field that could disagree with it.
 *
 * <p>The general form of that lesson: the reliable way to prevent an insecure direct object
 * reference is to remove the direct object reference. A check you have to remember to write
 * on every endpoint is a check you will one day forget on one.
 *
 * <p>Carries the id as well as the username because {@code medications.user_id} points at the
 * id. Looking the account up again on every request to turn a username into an id would be a
 * query per request for something the session already knows.
 */
public class AccountPrincipal implements UserDetails {

    private final String accountId;
    private final String username;
    private final String passwordHash;
    private final List<GrantedAuthority> authorities;

    /**
     * @param account the account this principal represents
     */
    public AccountPrincipal(Account account) {
        this.accountId = account.id();
        this.username = account.username();
        this.passwordHash = account.passwordHash();
        // Spring Security's hasRole("ADMIN") looks for the authority "ROLE_ADMIN". The
        // prefix is a convention it applies silently, so it is written out here rather than
        // left as a surprise for whoever next wonders why hasRole fails.
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name()));
    }

    /**
     * The account id, which is what owns rows in the database.
     *
     * @return the account id, never {@code null}
     */
    public String accountId() {
        return accountId;
    }

    /** {@inheritDoc} */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * The stored hash, which Spring Security compares the submitted password against.
     *
     * <p>Named {@code getPassword} by the {@link UserDetails} contract, which is unfortunate:
     * it returns a hash and nothing in this application ever holds a plaintext password
     * beyond the few lines of {@code AuthController} that hash it.
     *
     * @return the BCrypt hash
     */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** {@inheritDoc} */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * Keeps the hash out of logs and stack traces.
     *
     * <p>The default {@code toString} on a credentials-carrying object is a leak waiting for
     * the first debug log that prints the principal.
     *
     * @return a description with no secret in it
     */
    @Override
    public String toString() {
        return "AccountPrincipal[" + username + "]";
    }
}
