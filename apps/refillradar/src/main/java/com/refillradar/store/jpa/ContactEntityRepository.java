package com.refillradar.store.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@link ContactEntity} rows.
 */
public interface ContactEntityRepository extends JpaRepository<ContactEntity, String> {
}
