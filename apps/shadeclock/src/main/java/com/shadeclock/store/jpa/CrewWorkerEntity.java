package com.shadeclock.store.jpa;

import java.time.LocalDate;

import com.shadeclock.crew.Worker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The database row behind a {@link Worker}.
 *
 * <p>Separate from the domain record for the same reason as every other entity here: JPA needs
 * a no-arg constructor and mutable fields, and {@link Worker}'s compact constructor refuses to
 * exist without a {@code heatWorkStartedOn}. Keeping them apart means the validation survives.
 */
@Entity
@Table(name = "crew_workers")
public class CrewWorkerEntity {

    @Id
    private String id;

    /**
     * The owning crew.
     *
     * <p>{@code LAZY} on the parent side of the relationship: loading a worker must not drag
     * the whole crew and its other workers back with it. The crew always loads its workers,
     * never the other way round.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_id", nullable = false)
    private CrewEntity crew;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate heatWorkStartedOn;

    /**
     * The day a break of a week or more ended, or {@code null}.
     *
     * <p>Nullable on purpose, and the distinction matters: {@code null} means "no absence
     * recorded", which is not the same as "worked every day". The acclimatisation maths treats
     * a return from absence as restarting the adaptation clock, so a wrong default here would
     * quietly mark a returning worker as fully adapted on their first day back.
     */
    private LocalDate lastAbsenceEndedOn;

    /** Required by JPA. Not for application code - use {@link #fromDomain}. */
    protected CrewWorkerEntity() {
    }

    /**
     * Builds a row from a domain object.
     *
     * @param crew   the owning crew entity
     * @param worker the validated domain object
     * @return the entity to persist
     */
    static CrewWorkerEntity fromDomain(CrewEntity crew, Worker worker) {
        CrewWorkerEntity entity = new CrewWorkerEntity();
        entity.id = worker.id();
        entity.crew = crew;
        entity.name = worker.name();
        entity.heatWorkStartedOn = worker.heatWorkStartedOn();
        entity.lastAbsenceEndedOn = worker.lastAbsenceEndedOn();
        return entity;
    }

    /**
     * Rebuilds the domain object, re-running its validation.
     *
     * @return the domain object
     */
    Worker toDomain() {
        return new Worker(id, name, heatWorkStartedOn, lastAbsenceEndedOn);
    }
}
