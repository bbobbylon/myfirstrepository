package com.safeword.store.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@link CircleEntity} rows.
 *
 * <p>Every query here is by owner. There is no {@code findById} exposed upwards, because the
 * application has no legitimate reason to fetch a circle by an id a caller supplied.
 */
public interface CircleEntityRepository extends JpaRepository<CircleEntity, String> {

    /**
     * Finds the circle belonging to an account.
     *
     * @param ownerAccountId the owning account
     * @return the row, or empty
     */
    Optional<CircleEntity> findByOwnerAccountId(String ownerAccountId);

    /**
     * Whether an account already has a circle.
     *
     * @param ownerAccountId the owning account
     * @return {@code true} if a row exists
     */
    boolean existsByOwnerAccountId(String ownerAccountId);
}
