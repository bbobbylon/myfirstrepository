package com.refillradar.store.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@link AccountEntity} rows.
 */
public interface AccountEntityRepository extends JpaRepository<AccountEntity, String> {

    /**
     * Finds an account by username, ignoring case.
     *
     * <p>{@code IgnoreCase} generates {@code WHERE lower(username) = lower(?)}, which is the
     * expression the unique index in V2 is built on - so this lookup uses the index rather
     * than scanning every row.
     *
     * @param username the username as typed
     * @return the row, or empty
     */
    Optional<AccountEntity> findByUsernameIgnoreCase(String username);

    /**
     * Whether a username is taken, ignoring case.
     *
     * @param username the username as typed
     * @return {@code true} if a row exists
     */
    boolean existsByUsernameIgnoreCase(String username);
}
