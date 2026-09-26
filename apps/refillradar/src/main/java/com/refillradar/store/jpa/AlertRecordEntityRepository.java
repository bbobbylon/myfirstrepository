package com.refillradar.store.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@link AlertRecordEntity} rows.
 */
public interface AlertRecordEntityRepository extends JpaRepository<AlertRecordEntity, String> {
}
