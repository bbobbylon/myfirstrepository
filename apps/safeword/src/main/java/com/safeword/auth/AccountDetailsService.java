package com.safeword.auth;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.safeword.store.AccountRepository;

/**
 * Loads an account for Spring Security to authenticate against.
 *
 * <p>The exception message deliberately says nothing about whether the username exists.
 * "No such user" and "wrong password" as distinct responses let anyone enumerate who has an
 * account - which for this application is who has a family circle here.
 */
@Service
public class AccountDetailsService implements UserDetailsService {

    private final AccountRepository accounts;

    /**
     * @param accounts where accounts are stored
     */
    public AccountDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** {@inheritDoc} */
    @Override
    public AccountPrincipal loadUserByUsername(String username) {
        return accounts.findByUsername(username)
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }
}
