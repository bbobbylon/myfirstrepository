package com.renewalguard.store.jpa;

import java.time.LocalDate;

import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.BenefitProgram;
import com.renewalguard.domain.EnrollmentCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The database row behind a {@link BenefitCase}.
 *
 * <p><b>Why this exists instead of annotating the domain record.</b> {@link BenefitCase} is a
 * {@code record} whose compact constructor refuses a null {@code renewalDueOn} and a
 * non-positive {@code responseWindowDays}. JPA cannot manage a record: it needs a no-arg
 * constructor and mutable fields so Hibernate can build an instance and fill it in
 * afterwards. Adding those to {@code BenefitCase} would mean giving up exactly the validation
 * that makes it safe to hand to the projection maths.
 *
 * <p>So the two stay separate and this class maps between them. Same split as RefillRadar's
 * {@code MedicationEntity} - the boilerplate buys a domain object that cannot hold invalid
 * state, and a schema free to change without the domain noticing.
 */
@Entity
@Table(name = "benefit_cases")
public class BenefitCaseEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    /**
     * Stored as the enum's name, not its ordinal.
     *
     * <p>{@code EnumType.ORDINAL} is the JPA default and it is a trap: it writes the
     * constant's position, so inserting a new programme in the middle of
     * {@link BenefitProgram} silently re-labels every existing row. The name costs a few
     * bytes and survives reordering.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BenefitProgram program;

    @Column(nullable = false)
    private String stateCode;

    /** Stored by name, for the same reason as {@link #program}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentCategory category;

    @Column(nullable = false)
    private LocalDate renewalDueOn;

    private LocalDate noticeReceivedOn;

    private LocalDate addressConfirmedOn;

    /** {@code Integer}, not {@code int}: null means "the user did not tell us". */
    private Integer responseWindowDays;

    /** Required by JPA. Not for application code - use {@link #fromDomain}. */
    protected BenefitCaseEntity() {
    }

    /**
     * Builds a row from a domain object.
     *
     * @param benefitCase the validated domain object
     * @return the entity to persist
     */
    public static BenefitCaseEntity fromDomain(BenefitCase benefitCase) {
        BenefitCaseEntity entity = new BenefitCaseEntity();
        entity.id = benefitCase.id();
        entity.userId = benefitCase.userId();
        entity.program = benefitCase.program();
        entity.stateCode = benefitCase.stateCode();
        entity.category = benefitCase.category();
        entity.renewalDueOn = benefitCase.renewalDueOn();
        entity.noticeReceivedOn = benefitCase.noticeReceivedOn();
        entity.addressConfirmedOn = benefitCase.addressConfirmedOn();
        entity.responseWindowDays = benefitCase.responseWindowDays();
        return entity;
    }

    /**
     * Rebuilds the domain object, re-running its validation.
     *
     * <p>The compact constructor runs again here, so a row that somehow violates the
     * invariant fails on read rather than flowing into a reminder someone acts on.
     *
     * @return the domain object
     */
    public BenefitCase toDomain() {
        return new BenefitCase(id, userId, program, stateCode, category, renewalDueOn,
                noticeReceivedOn, addressConfirmedOn, responseWindowDays);
    }
}
