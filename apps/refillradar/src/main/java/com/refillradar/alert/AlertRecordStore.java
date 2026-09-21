package com.refillradar.alert;

import java.util.Optional;

/**
 * Where {@link AlertLedger} keeps what it has already sent.
 *
 * <p>Separated from {@link AlertLedger} so that the <em>policy</em> - what counts as an
 * escalation, how long the repeat window is - stays one testable class with no database,
 * while <em>persistence</em> varies. The policy is the part with the interesting bugs; it
 * should not need a schema to exercise.
 */
public interface AlertRecordStore {

    /**
     * Looks up the last alert sent under a key.
     *
     * @param alertKey the alert's identity
     * @return the previous entry, or empty if none
     */
    Optional<AlertLedger.Entry> find(String alertKey);

    /**
     * Records that an alert was sent, replacing any previous entry for the same key.
     *
     * @param alertKey the alert's identity
     * @param entry    what was sent and when
     */
    void put(String alertKey, AlertLedger.Entry entry);

    /**
     * Removes everything. Test support only.
     */
    void clear();
}
