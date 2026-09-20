package com.shadeclock.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.shadeclock.crew.Crew;

/**
 * In-memory {@link CrewRepository} for v0.1.
 *
 * <p><b>Data is lost on restart.</b> Acceptable for proving the engine; not acceptable for a
 * crew relying on it. PostgreSQL is the v0.2 task.
 *
 * <p>{@link ConcurrentHashMap} rather than {@link java.util.HashMap} because a web
 * application is multi-threaded by definition, and a plain {@code HashMap} under concurrent
 * writes can corrupt its own structure rather than merely losing an entry.
 */
@Repository
public class InMemoryCrewRepository implements CrewRepository {

    private final Map<String, Crew> storage = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public Crew save(Crew crew) {
        storage.put(crew.id(), crew);
        return crew;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Crew> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    /** {@inheritDoc} */
    @Override
    public List<Crew> findAll() {
        return List.copyOf(storage.values());
    }

    /** {@inheritDoc} */
    @Override
    public boolean deleteById(String id) {
        return storage.remove(id) != null;
    }
}
