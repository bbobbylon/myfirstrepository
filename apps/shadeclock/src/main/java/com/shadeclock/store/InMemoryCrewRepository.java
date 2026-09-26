package com.shadeclock.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.shadeclock.crew.Crew;

/**
 * In-memory {@link CrewRepository}, kept for the {@code memory} profile.
 *
 * <p><b>Data is lost on restart</b>, so this is no longer the default: since v0.2 PostgreSQL is,
 * and an unreachable database stops the application starting. This implementation survives because
 * unit tests that exercise the scheduling maths should not need a database to run, and because
 * {@code MemoryProfileTest} proves the application can still boot without one - which is what keeps
 * the storage seam honest rather than decorative.
 *
 * <p>{@link ConcurrentHashMap} rather than {@link java.util.HashMap} because a web application is
 * multi-threaded by definition, and a plain {@code HashMap} under concurrent writes can corrupt its
 * own structure rather than merely losing an entry.
 */
@Repository
@Profile("memory")
public class InMemoryCrewRepository implements CrewRepository {

    /**
     * A crew stored beside the account that owns it.
     *
     * <p>The owner is held here rather than on {@link Crew} so the domain record stays unchanged,
     * which is the same split the JPA implementation makes between {@code crews.owner_account_id}
     * and the object the scheduler receives.
     *
     * @param ownerAccountId the owning account
     * @param crew           the crew itself
     */
    private record OwnedCrew(String ownerAccountId, Crew crew) {
    }

    private final Map<String, OwnedCrew> storage = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public Crew save(String ownerAccountId, Crew crew) {
        // A plain put() had exactly the hijack the JPA implementation had: writing under an id
        // that already belongs to another account silently replaced their crew AND its owner. The
        // two implementations have to agree about this, or a test against the fast one proves
        // nothing about the real one - which is why this refusal is duplicated rather than left
        // to the database.
        OwnedCrew existing = storage.get(crew.id());
        if (existing != null && !existing.ownerAccountId().equals(ownerAccountId)) {
            throw new IllegalStateException(
                    "Refusing to write crew " + crew.id() + ": that id belongs to another account");
        }
        storage.put(crew.id(), new OwnedCrew(ownerAccountId, crew));
        return crew;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Crew> findByIdAndOwner(String id, String ownerAccountId) {
        // The owner check is part of the lookup rather than the caller's job, exactly as it is
        // part of the SQL WHERE clause in the JPA implementation.
        return Optional.ofNullable(storage.get(id))
                .filter(owned -> owned.ownerAccountId().equals(ownerAccountId))
                .map(OwnedCrew::crew);
    }

    /** {@inheritDoc} */
    @Override
    public List<Crew> findByOwner(String ownerAccountId) {
        return storage.values().stream()
                .filter(owned -> owned.ownerAccountId().equals(ownerAccountId))
                .map(OwnedCrew::crew)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public boolean deleteByIdAndOwner(String id, String ownerAccountId) {
        OwnedCrew existing = storage.get(id);
        if (existing == null || !existing.ownerAccountId().equals(ownerAccountId)) {
            return false;
        }
        // The two-argument remove, not remove(id): it deletes only while the stored value is still
        // the one just checked, so two concurrent deletes cannot both report success and a write
        // that slipped in between is not silently discarded. Records compare by value, which is
        // what makes the second argument meaningful.
        return storage.remove(id, existing);
    }
}
