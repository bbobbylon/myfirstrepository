package com.safeword.store.jpa;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.safeword.domain.FamilyCircle;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * The database row behind a {@link FamilyCircle}.
 *
 * <p>Separate from the domain record because JPA needs a mutable class with a no-arg
 * constructor, and {@link FamilyCircle} is a record that validates itself.
 *
 * <p><b>No passphrase field</b>, matching the schema. See {@code V1__initial_schema.sql} for
 * why that absence is the feature rather than an omission.
 */
@Entity
@Table(name = "circles")
public class CircleEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String ownerAccountId;

    @Column(nullable = false)
    private String name;

    private LocalDate passphraseAgreedOn;

    /**
     * The circle's members.
     *
     * <p>Fetched eagerly on purpose. {@code open-in-view} is off, so a lazy collection
     * touched after the transaction ends throws rather than quietly issuing another query -
     * and every read of a circle needs its members anyway. The list is bounded by how many
     * people are in a family, so this is the right shape rather than a workaround.
     *
     * <p>{@code orphanRemoval} is what makes "save the circle without Dave" actually remove
     * Dave, instead of leaving a responder the family thinks they deleted.
     */
    @OneToMany(mappedBy = "circle", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.EAGER)
    private List<CircleMemberEntity> members = new ArrayList<>();

    /** Required by JPA. Not for application code - use {@link #create}. */
    protected CircleEntity() {
    }

    /**
     * Builds a new row for an owner.
     *
     * @param id             the circle id
     * @param ownerAccountId the owning account
     * @return the entity, with no members yet
     */
    public static CircleEntity create(String id, String ownerAccountId) {
        CircleEntity entity = new CircleEntity();
        entity.id = id;
        entity.ownerAccountId = ownerAccountId;
        return entity;
    }

    /**
     * Overwrites the circle's contents from a domain object, keeping this row's id.
     *
     * <p>The id is deliberately not taken from the domain object: a circle's id is assigned
     * once by the server and stays put, so updating a circle cannot silently mint a new one.
     *
     * @param circle the desired state
     */
    public void replaceWith(FamilyCircle circle) {
        this.name = circle.name();
        this.passphraseAgreedOn = circle.passphraseAgreedOn();
        // Clearing the managed list (rather than assigning a new one) is what lets
        // orphanRemoval see the departures.
        this.members.clear();
        circle.members().forEach(member ->
                this.members.add(CircleMemberEntity.of(this, member)));
    }

    /**
     * Rebuilds the domain object, re-running its validation.
     *
     * @return the domain object
     */
    public FamilyCircle toDomain() {
        return new FamilyCircle(id, name, passphraseAgreedOn,
                members.stream().map(CircleMemberEntity::toDomain).toList());
    }
}
