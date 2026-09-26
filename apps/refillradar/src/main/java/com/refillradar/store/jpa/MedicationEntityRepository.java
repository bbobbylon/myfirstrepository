package com.refillradar.store.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@link MedicationEntity} rows.
 *
 * <p>No implementation is written: Spring Data generates one at startup from the method
 * names. {@code findByUserId} becomes {@code WHERE user_id = ?}, which is why the name has
 * to match the field exactly - a typo here is a runtime failure at context load, not a
 * compile error.
 *
 * <p>This interface is deliberately not the one the rest of the application depends on.
 * {@code JpaMedicationRepository} adapts it to {@code MedicationRepository}, so the matcher
 * and the sync job keep talking to our own interface in terms of domain objects.
 */
public interface MedicationEntityRepository extends JpaRepository<MedicationEntity, String> {

    /**
     * Every medication belonging to one user.
     *
     * @param userId the owner
     * @return that user's rows
     */
    List<MedicationEntity> findByUserId(String userId);
}
