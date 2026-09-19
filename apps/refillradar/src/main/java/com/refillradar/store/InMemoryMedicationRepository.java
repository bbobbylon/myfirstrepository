package com.refillradar.store;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.refillradar.domain.Medication;

/**
 * In-memory {@link MedicationRepository} for v0.1.
 *
 * <p><b>Data is lost on restart.</b> That is acceptable for proving the engine and running
 * tests, and unacceptable for real users. PostgreSQL via Spring Data JPA is the v0.2 task;
 * see {@link MedicationRepository} for why that swap is cheap.
 *
 * <p>Uses {@link ConcurrentHashMap} rather than a plain {@link java.util.HashMap} because a
 * web application is multi-threaded by definition: the scheduled sync and an incoming HTTP
 * request can touch this map at the same moment. A plain {@code HashMap} under concurrent
 * writes does not merely lose an entry - it can corrupt its internal structure and hang.
 * Choosing the concurrent collection costs nothing here and removes a whole class of bug
 * that only ever reproduces under load.
 */
@Repository
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
