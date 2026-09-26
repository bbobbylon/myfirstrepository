package com.refillradar.store.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The database row holding where one user can be reached.
 *
 * <p>Keyed on {@code userId} rather than a surrogate id, because a user has exactly one
 * contact route in v0.3. Making the natural key the primary key means "one email per user"
 * is enforced by the database instead of by whichever code path happens to write last.
 */
@Entity
@Table(name = "contacts")
public class ContactEntity {

    @Id
    private String userId;

    @Column(nullable = false)
    private String email;

    /** Required by JPA. Not for application code - use {@link #of}. */
    protected ContactEntity() {
    }

    /**
     * Builds a contact row.
     *
     * @param userId the user
     * @param email  where to reach them
     * @return the entity to persist
     */
    public static ContactEntity of(String userId, String email) {
        ContactEntity entity = new ContactEntity();
        entity.userId = userId;
        entity.email = email;
        return entity;
    }

    /**
     * @return the user this row belongs to
     */
    public String getUserId() {
        return userId;
    }

    /**
     * @return the email address
     */
    public String getEmail() {
        return email;
    }
}
