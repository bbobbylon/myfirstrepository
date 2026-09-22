package com.safeword.store.jpa;

import java.time.Instant;

import com.safeword.domain.Account;
import com.safeword.domain.AccountRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The database row behind an {@link Account}.
 *
 * <p>Same split as {@code MedicationEntity} and for the same reason: the domain type is a
 * validating record, which JPA cannot manage.
 *
 * <p>{@code EnumType.STRING} again - storing {@link AccountRole} by position would mean
 * that inserting a role into the middle of the enum silently promotes existing accounts.
 * On a privilege field that is not a data bug, it is a privilege escalation.
 */
@Entity
@Table(name = "users")
public class AccountEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountRole role;

    @Column(nullable = false)
    private Instant createdOn;

    /** Required by JPA. Not for application code - use {@link #fromDomain}. */
    protected AccountEntity() {
    }

    /**
     * Builds a row from a domain object.
     *
     * @param account the validated domain object
     * @return the entity to persist
     */
    public static AccountEntity fromDomain(Account account) {
        AccountEntity entity = new AccountEntity();
        entity.id = account.id();
        entity.username = account.username();
        entity.passwordHash = account.passwordHash();
        entity.role = account.role();
        entity.createdOn = account.createdOn();
        return entity;
    }

    /**
     * Rebuilds the domain object, re-running its validation.
     *
     * @return the domain object
     */
    public Account toDomain() {
        return new Account(id, username, passwordHash, role, createdOn);
    }
}
