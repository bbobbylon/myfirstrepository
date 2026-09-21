package com.refillradar.store.jpa;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.refillradar.alert.AlertLedger;
import com.refillradar.alert.AlertRecordStore;

/**
 * PostgreSQL-backed {@link AlertRecordStore}. The default from v0.3 onwards.
 *
 * <p>This is the change that makes de-duplication actually work. With the ledger in memory a
 * restart re-sent every alert; with it in a table, what the application has told a user
 * outlives the process that told them.
 */
@Component
@Profile("!memory")
public class JpaAlertRecordStore implements AlertRecordStore {

    private final AlertRecordEntityRepository rows;

    /**
     * @param rows Spring Data access to the alert_records table
     */
    public JpaAlertRecordStore(AlertRecordEntityRepository rows) {
        this.rows = rows;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<AlertLedger.Entry> find(String alertKey) {
        return rows.findById(alertKey)
                .map(row -> new AlertLedger.Entry(row.getRisk(), row.getSentAt()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void put(String alertKey, AlertLedger.Entry entry) {
        rows.save(AlertRecordEntity.of(alertKey, entry.risk(), entry.sentAt()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void clear() {
        rows.deleteAll();
    }
}
