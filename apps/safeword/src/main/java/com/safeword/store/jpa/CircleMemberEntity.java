package com.safeword.store.jpa;

import com.safeword.domain.CircleMember;
import com.safeword.domain.MemberRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The database row behind a {@link CircleMember}.
 *
 * <p>{@code EnumType.STRING}, never the {@code ORDINAL} default: storing the role by
 * position means inserting a value into the middle of {@link MemberRole} silently turns
 * responders into protected people, and the database would have no way to tell.
 */
@Entity
@Table(name = "circle_members")
public class CircleMemberEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "circle_id", nullable = false)
    private CircleEntity circle;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    private String contact;

    /** Required by JPA. Not for application code - use {@link #of}. */
    protected CircleMemberEntity() {
    }

    /**
     * Builds a row from a domain object.
     *
     * @param circle the owning circle
     * @param member the validated domain object
     * @return the entity to persist
     */
    public static CircleMemberEntity of(CircleEntity circle, CircleMember member) {
        CircleMemberEntity entity = new CircleMemberEntity();
        entity.id = member.id();
        entity.circle = circle;
        entity.name = member.name();
        entity.role = member.role();
        entity.contact = member.contact();
        return entity;
    }

    /**
     * Rebuilds the domain object, re-running its validation.
     *
     * @return the domain object
     */
    public CircleMember toDomain() {
        return new CircleMember(id, name, role, contact);
    }
}
