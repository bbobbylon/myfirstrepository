package com.safeword.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.safeword.domain.FamilyCircle;

/**
 * In-memory storage for family circles.
 *
 * <p>Combined interface and implementation here, unlike the other apps, because there is
 * genuinely less to store: SafeWord holds names, contact routes and one date. The heavier
 * repository abstraction in RefillRadar and RenewalGuard earns its place because those apps
 * will grow real schemas; this one may never need more than a small table.
 *
 * <p><b>Data is lost on restart</b>, which is a v0.1 limitation like the others.
 */
@Repository
public class CircleRepository {

    private final Map<String, FamilyCircle> storage = new ConcurrentHashMap<>();

    /**
     * Saves a circle, replacing any existing entry with the same id.
     *
     * @param circle the circle to store
     * @return the stored circle
     */
    public FamilyCircle save(FamilyCircle circle) {
        storage.put(circle.id(), circle);
        return circle;
    }

    /**
     * Finds a circle by id.
     *
     * @param id the circle id
     * @return the circle, or empty if unknown
     */
    public Optional<FamilyCircle> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    /**
     * Returns every stored circle.
     *
     * @return all circles, never {@code null}
     */
    public List<FamilyCircle> findAll() {
        return List.copyOf(storage.values());
    }
}
