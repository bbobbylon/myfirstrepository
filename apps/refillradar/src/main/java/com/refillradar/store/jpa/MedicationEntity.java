package com.refillradar.store.jpa;

import java.time.LocalDate;

import com.refillradar.domain.Medication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The database row behind a {@link Medication}.
 *
 * <p><b>Why this exists instead of annotating the domain record.</b> {@link Medication} is a
 * {@code record} with a compact constructor that rejects a non-positive {@code daysSupply}
 * and a null {@code lastFilledOn}. JPA cannot manage a record: it needs a no-arg constructor
 * and mutable fields so Hibernate can build an instance and fill it in afterwards. Adding
 * those to {@code Medication} would mean giving up exactly the validation that makes it safe
 * to hand to the projection maths.
 *
 * <p>So the two stay separate and this class maps between them. The cost is the boilerplate
 * below; what it buys is a domain object that cannot hold invalid state, and a schema that
 * can change without the domain noticing.
 */
@Entity
@Table(name = "medications")
public class MedicationEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String searchTerm;

    @Column(nullable = false)
    private LocalDate lastFilledOn;

    @Column(nullable = false)
    private int daysSupply;

    /** Required by JPA. Not for application code - use {@link #fromDomain}. */
    protected MedicationEntity() {
    }

    /**
     * Builds a row from a domain object.
     *
     * @param medication the validated domain object
     * @return the entity to persist
     */
    public static MedicationEntity fromDomain(Medication medication) {
        MedicationEntity entity = new MedicationEntity();
        entity.id = medication.id();
        entity.userId = medication.userId();
        entity.displayName = medication.displayName();
        entity.searchTerm = medication.searchTerm();
        entity.lastFilledOn = medication.lastFilledOn();
        entity.daysSupply = medication.daysSupply();
        return entity;
    }

    /**
     * Rebuilds the domain object, re-running its validation.
     *
     * <p>The compact constructor runs again here, so a row that somehow violates the
     * invariant fails on read rather than flowing into an alert.
     *
     * @return the domain object
     */
    public Medication toDomain() {
        return new Medication(id, userId, displayName, searchTerm, lastFilledOn, daysSupply);
    }
}
