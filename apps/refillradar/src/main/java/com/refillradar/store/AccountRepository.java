package com.refillradar.store;

import java.util.Optional;

import com.refillradar.domain.Account;

/**
 * Stores registered accounts.
 */
public interface AccountRepository {

    /**
     * Saves an account, replacing any existing entry with the same id.
     *
     * @param account the account to store
     * @return the stored account
     */
    Account save(Account account);

    /**
     * Finds an account by username, ignoring case.
     *
     * <p>Case-insensitive because people do not remember whether they capitalised their own
     * username, and a login that fails for that reason is indistinguishable from a wrong
     * password. The unique index is on {@code lower(username)} so this cannot find two.
     *
     * @param username the username as typed
     * @return the account, or empty
     */
    Optional<Account> findByUsername(String username);

    /**
     * Finds an account by id.
     *
     * @param id the account id
     * @return the account, or empty
     */
    Optional<Account> findById(String id);

    /**
     * Whether a username is already taken, ignoring case.
     *
     * @param username the username as typed
     * @return {@code true} if it exists
     */
    boolean usernameExists(String username);
}
