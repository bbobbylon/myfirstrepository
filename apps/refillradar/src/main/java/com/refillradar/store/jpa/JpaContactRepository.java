package com.refillradar.store.jpa;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.refillradar.store.ContactRepository;

/**
 * PostgreSQL-backed {@link ContactRepository}. The default from v0.3 onwards.
 */
@Repository
@Profile("!memory")
public class JpaContactRepository implements ContactRepository {

    private final ContactEntityRepository rows;

    /**
     * @param rows Spring Data access to the contacts table
     */
    public JpaContactRepository(ContactEntityRepository rows) {
        this.rows = rows;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void setEmail(String userId, String email) {
        rows.save(ContactEntity.of(userId, email));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<String> findEmail(String userId) {
        return rows.findById(userId).map(ContactEntity::getEmail);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public boolean isReachable(String userId) {
        return rows.existsById(userId);
    }
}
