package com.renewalguard.store.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.renewalguard.domain.BenefitCase;
import com.renewalguard.store.BenefitCaseRepository;

/**
 * PostgreSQL-backed {@link BenefitCaseRepository}. The default from v0.2 onwards.
 *
 * <p>Active unless the {@code memory} profile is on, so a deployment that cannot reach its
 * database <b>fails to start</b> rather than quietly running the amnesiac in-memory store.
 * For this app that is the only honest failure mode: a reminder service that forgot your
 * deadline looks identical to one that decided today is not a reminder day.
 */
@Repository
@Profile("!memory")
public class JpaBenefitCaseRepository implements BenefitCaseRepository {

    private final BenefitCaseEntityRepository rows;

    /**
     * @param rows Spring Data access to the benefit_cases table
     */
    public JpaBenefitCaseRepository(BenefitCaseEntityRepository rows) {
        this.rows = rows;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public BenefitCase save(BenefitCase benefitCase) {
        rows.save(BenefitCaseEntity.fromDomain(benefitCase));
        return benefitCase;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<BenefitCase> findByIdAndUserId(String id, String userId) {
        return rows.findByIdAndUserId(id, userId).map(BenefitCaseEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<BenefitCase> findByUserId(String userId) {
        return rows.findByUserId(userId).stream().map(BenefitCaseEntity::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<BenefitCase> findAll() {
        return rows.findAll().stream().map(BenefitCaseEntity::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean deleteByIdAndUserId(String id, String userId) {
        return rows.deleteByIdAndUserId(id, userId) > 0;
    }
}
