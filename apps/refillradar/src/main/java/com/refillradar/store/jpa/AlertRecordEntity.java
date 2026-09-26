package com.refillradar.store.jpa;

import java.time.Instant;

import com.refillradar.domain.SupplyRisk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The database row recording that an alert was sent.
 *
 * <p>{@link Enumerated}{@code (EnumType.STRING)} is deliberate and load-bearing. The default
 * is {@code ORDINAL}, which stores the enum's position as an integer - so inserting a new
 * value into the middle of {@link SupplyRisk} would silently reinterpret every existing row.
 * Here that would mean a stored CRITICAL reading back as HIGH, and the escalation check in
 * {@code AlertLedger} suppressing an alert it should send. Storing the name costs a few
 * bytes and removes the failure entirely.
 */
@Entity
@Table(name = "alert_records")
public class AlertRecordEntity {

    @Id
    private String alertKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplyRisk risk;

    @Column(nullable = false)
    private Instant sentAt;

    /** Required by JPA. Not for application code - use {@link #of}. */
    protected AlertRecordEntity() {
    }

    /**
     * Builds an alert record.
     *
     * @param alertKey identity of the alert, from {@code AlertLedger}
     * @param risk     the urgency at the time it was sent
     * @param sentAt   when it was sent
     * @return the entity to persist
     */
    public static AlertRecordEntity of(String alertKey, SupplyRisk risk, Instant sentAt) {
        AlertRecordEntity entity = new AlertRecordEntity();
        entity.alertKey = alertKey;
        entity.risk = risk;
        entity.sentAt = sentAt;
        return entity;
    }

    /**
     * @return the alert's identity
     */
    public String getAlertKey() {
        return alertKey;
    }

    /**
     * @return the urgency recorded
     */
    public SupplyRisk getRisk() {
        return risk;
    }

    /**
     * @return when the alert was sent
     */
    public Instant getSentAt() {
        return sentAt;
    }
}
