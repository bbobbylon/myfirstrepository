package com.shadeclock.store.jpa;

import java.util.ArrayList;
import java.util.List;

import com.shadeclock.crew.Crew;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * The database row behind a {@link Crew}, plus the account that owns it.
 *
 * <p><b>Why this exists instead of annotating the domain record.</b> {@link Crew} is a
 * {@code record} whose compact constructor defensive-copies its worker list, and {@code Worker}
 * refuses a null {@code heatWorkStartedOn}. JPA cannot manage either: it needs a no-arg
 * constructor and mutable fields so Hibernate can build an instance and fill it in afterwards.
 *
 * <p><b>And why {@code ownerAccountId} lives here rather than on {@link Crew}.</b> The owner is
 * a storage and authorisation fact, not a scheduling one - the heat maths has no opinion about
 * who is logged in. Keeping it on the entity means the domain record is unchanged by v0.2, so
 * all 54 existing unit tests still construct crews exactly as before, and no account id leaks
 * into a response that had no reason to carry one. Same split SafeWord made.
 */
@Entity
@Table(name = "crews")
public class CrewEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String ownerAccountId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String siteLabel;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private String jurisdiction;

    /**
     * The crew's workers.
     *
     * <p>{@code EAGER} because a crew is never useful without them: every read exists to build
     * a schedule, and that needs each worker's acclimatisation. With {@code open-in-view} off,
     * a lazy list touched after the transaction would throw rather than load.
     *
     * <p>{@code orphanRemoval} is what makes "save the crew without Dave" actually delete Dave's
     * row instead of leaving it orphaned with a dangling parent.
     */
    @OneToMany(mappedBy = "crew", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.EAGER)
    private List<CrewWorkerEntity> workers = new ArrayList<>();

    /** Required by JPA. Not for application code - use {@link #create}. */
    protected CrewEntity() {
    }

    /**
     * Creates an empty row for a crew belonging to one account.
     *
     * @param id             the crew id
     * @param ownerAccountId the owning account
     * @return the new entity, before {@link #replaceWith} fills it in
     */
    static CrewEntity create(String id, String ownerAccountId) {
        CrewEntity entity = new CrewEntity();
        entity.id = id;
        entity.ownerAccountId = ownerAccountId;
        return entity;
    }

    /**
     * Overwrites this row's fields and worker list from a domain object.
     *
     * <p>Mutates in place rather than building a fresh entity, because replacing the collection
     * instance would detach the one Hibernate is tracking and {@code orphanRemoval} would never
     * see the departures. The list is cleared and refilled instead.
     *
     * @param crew the crew to copy from
     */
    void replaceWith(Crew crew) {
        this.name = crew.name();
        this.siteLabel = crew.siteLabel();
        this.latitude = crew.latitude();
        this.longitude = crew.longitude();
        this.jurisdiction = crew.jurisdiction();

        workers.clear();
        crew.workers().forEach(worker -> workers.add(CrewWorkerEntity.fromDomain(this, worker)));
    }

    /**
     * Rebuilds the domain object, re-running its validation.
     *
     * <p>The record's compact constructor runs again here, so a row that somehow violates an
     * invariant fails on read rather than flowing into a schedule a foreman acts on.
     *
     * @return the domain object, without the owner - see the class comment
     */
    Crew toDomain() {
        return new Crew(id, name, siteLabel, latitude, longitude, jurisdiction,
                workers.stream().map(CrewWorkerEntity::toDomain).toList());
    }
}
