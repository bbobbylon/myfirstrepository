package com.safeword.store.jpa;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.safeword.domain.FamilyCircle;
import com.safeword.store.CircleRepository;

/**
 * PostgreSQL-backed {@link CircleRepository}. The default from v0.2 onwards.
 *
 * <p>v0.1 lost every circle on restart. For an app whose whole job is to be ready at the
 * moment a scam call arrives, "set it up again" after a deploy is not a minor inconvenience:
 * the family believes they are protected, and nothing tells them they are not.
 */
@Repository
@Profile("!memory")
public class JpaCircleRepository implements CircleRepository {

    private final CircleEntityRepository rows;

    /**
     * @param rows Spring Data access to the circles table
     */
    public JpaCircleRepository(CircleEntityRepository rows) {
        this.rows = rows;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public FamilyCircle save(String ownerAccountId, FamilyCircle circle) {
        CircleEntity entity = rows.findByOwnerAccountId(ownerAccountId)
                .orElseGet(() -> CircleEntity.create(circle.id(), ownerAccountId));
        entity.replaceWith(circle);
        return rows.save(entity).toDomain();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<FamilyCircle> findByOwner(String ownerAccountId) {
        return rows.findByOwnerAccountId(ownerAccountId).map(CircleEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean existsForOwner(String ownerAccountId) {
        return rows.existsByOwnerAccountId(ownerAccountId);
    }
}
