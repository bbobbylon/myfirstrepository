package com.refillradar.store;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.refillradar.domain.Medication;

/**
 * In-memory {@link MedicationRepository}, for the {@code memory} profile and for unit tests.
 *
 * <p><b>Data is lost on restart.</b> Since v0.3 this is no longer the default - PostgreSQL
 * is. It survives for unit tests, which construct it directly and need no database, and for
 * the {@code memory} profile, a documented demo mode.
 *
 * <p>Uses {@link ConcurrentHashMap} rather than a plain {@link java.util.HashMap} because a
 * web application is multi-threaded by definition: the scheduled sync and an incoming HTTP
 * request can touch this map at the same moment. A plain {@code HashMap} under concurrent
 * writes does not merely lose an entry - it can corrupt its internal structure and hang.
 * Choosing the concurrent collection costs nothing here and removes a whole class of bug
 * that only ever reproduces under load.
 */
@Repository
@Profile("memory")
public class InMemoryMedicationRepository implements MedicationRepository {

    private final Map<String, Medication> storage = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public Medication save(Medication medication) {
        storage.put(medication.id(), medication);
        return medication;
    }

    /** {@inheritDoc} */
    @Override
    public List<Medication> findByUserId(String userId) {
        return storage.values().stream()
                .filter(medication -> medication.userId().equals(userId))
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<Medication> findAll() {
        return List.copyOf(storage.values());
    }

    /** {@inheritDoc} */
    @Override
    public boolean deleteById(String id) {
        return storage.remove(id) != null;
    }
}
