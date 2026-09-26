package com.shadeclock.store.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.shadeclock.crew.Crew;
import com.shadeclock.store.CrewRepository;

/**
 * PostgreSQL-backed {@link CrewRepository}. The default from v0.2 onwards.
 *
 * <p>Active unless the {@code memory} profile is on, so a deployment that cannot reach its
 * database <b>fails to start</b> rather than quietly running the amnesiac in-memory store. For
 * this app the asymmetry is stark: a crew whose roster silently vanished would see the app open,
 * get a schedule built from an empty crew, and be told nobody needs extra precautions.
 */
@Repository
@Profile("!memory")
public class JpaCrewRepository implements CrewRepository {

    private final CrewEntityRepository rows;

    /**
     * @param rows Spring Data access to the crews table
     */
    public JpaCrewRepository(CrewEntityRepository rows) {
        this.rows = rows;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Crew save(String ownerAccountId, Crew crew) {
        // Reuse the existing row when it is THIS account's, so an update replaces the worker
        // list in place and orphanRemoval deletes whoever left the crew.
        Optional<CrewEntity> own = rows.findByIdAndOwnerAccountId(crew.id(), ownerAccountId);

        // The owner-scoped lookup above is necessary and, on its own, NOT SUFFICIENT - and the
        // gap it leaves is worth spelling out, because a test caught it here rather than a
        // reviewer catching it later.
        //
        // When the lookup finds nothing, that can mean two very different things: the id is
        // genuinely new, or it belongs to somebody else. Treating both as "new" and calling
        // rows.save() is a hijack: JPA's save() on an entity whose primary key already exists
        // is a MERGE, so it UPDATEs the other account's row - overwriting their roster and
        // reassigning owner_account_id to the caller. The owner-scoped read made the write
        // unsafe by hiding the row it was about to clobber.
        //
        // So an id that exists but is not ours is refused outright. IllegalStateException
        // rather than a quiet no-op or a 404: crew ids are server-generated UUIDs, so this
        // cannot happen through ordinary use. It means a bug or an attack, and both deserve to
        // be loud rather than absorbed.
        if (own.isEmpty() && rows.existsById(crew.id())) {
            throw new IllegalStateException(
                    "Refusing to write crew " + crew.id() + ": that id belongs to another account");
        }

        CrewEntity entity = own.orElseGet(() -> CrewEntity.create(crew.id(), ownerAccountId));
        entity.replaceWith(crew);
        return rows.save(entity).toDomain();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<Crew> findByIdAndOwner(String id, String ownerAccountId) {
        return rows.findByIdAndOwnerAccountId(id, ownerAccountId).map(CrewEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<Crew> findByOwner(String ownerAccountId) {
        return rows.findByOwnerAccountId(ownerAccountId).stream()
                .map(CrewEntity::toDomain).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean deleteByIdAndOwner(String id, String ownerAccountId) {
        return rows.deleteByIdAndOwnerAccountId(id, ownerAccountId) > 0;
    }
}
