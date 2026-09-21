package com.refillradar.alert;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In-memory {@link AlertRecordStore}, for the {@code memory} profile and for unit tests.
 *
 * <p><b>This was the v0.2 default, and it was a real bug.</b> A restart emptied the ledger,
 * so the next nightly run re-sent every alert the application had ever sent - exactly the
 * notification fatigue {@link AlertLedger} exists to prevent. v0.3 makes the database the
 * default for that reason.
 */
@Component
@Profile("memory")
public class InMemoryAlertRecordStore implements AlertRecordStore {

    private final Map<String, AlertLedger.Entry> sent = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public Optional<AlertLedger.Entry> find(String alertKey) {
        return Optional.ofNullable(sent.get(alertKey));
    }

    /** {@inheritDoc} */
    @Override
    public void put(String alertKey, AlertLedger.Entry entry) {
        sent.put(alertKey, entry);
    }

    /** {@inheritDoc} */
    @Override
    public void clear() {
        sent.clear();
    }
}
