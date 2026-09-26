package com.refillradar.store.jpa;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.refillradar.domain.Medication;
import com.refillradar.store.MedicationRepository;

/**
 * PostgreSQL-backed {@link MedicationRepository}. The default from v0.3 onwards.
 *
 * <p>Active unless the {@code memory} profile is on, so a deployment that cannot reach its
 * database <b>fails to start</b> rather than quietly running the amnesiac in-memory store.
 * That is the opposite default from the shortage feed, and the asymmetry is the point: a
 * missing feed is visible in the response, whereas silent data loss looks exactly like
 * working software right up until a user's list is empty.
 */
@Repository
@Profile("!memory")
public class JpaMedicationRepository implements MedicationRepository {

    private final MedicationEntityRepository rows;

    /**
     * @param rows Spring Data access to the medications table
     */
    public JpaMedicationRepository(MedicationEntityRepository rows) {
        this.rows = rows;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Medication save(Medication medication) {
        rows.save(MedicationEntity.fromDomain(medication));
        return medication;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<Medication> findByUserId(String userId) {
        return rows.findByUserId(userId).stream().map(MedicationEntity::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<Medication> findAll() {
        return rows.findAll().stream().map(MedicationEntity::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean deleteById(String id) {
        if (!rows.existsById(id)) {
            return false;
        }
        rows.deleteById(id);
        return true;
    }
}
