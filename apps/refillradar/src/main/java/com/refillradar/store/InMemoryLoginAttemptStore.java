package com.refillradar.store;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * In-memory {@link LoginAttemptStore}, for the {@code memory} profile and for unit tests.
 *
 * <p><b>Not the default, and that is the point.</b> A throttle that forgets on restart is a
 * throttle an attacker clears by waiting for the next deploy, and one that lives in a single
 * JVM gives every replica behind a load balancer its own full budget - three instances mean
 * three times the guesses. Both failures are invisible in development, where there is one
 * process that never restarts mid-attack.
 */
@Repository
@Profile("memory")
public class InMemoryLoginAttemptStore implements LoginAttemptStore {

    private final Map<String, List<Instant>> attempts = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public void record(String attemptKey, Instant when) {
        attempts.computeIfAbsent(attemptKey, key -> new CopyOnWriteArrayList<>()).add(when);
    }

    /** {@inheritDoc} */
    @Override
    public int countSince(String attemptKey, Instant since) {
        return (int) attempts.getOrDefault(attemptKey, List.of()).stream()
                .filter(when -> when.isAfter(since))
                .count();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Instant> earliestSince(String attemptKey, Instant since) {
        return attempts.getOrDefault(attemptKey, List.of()).stream()
                .filter(when -> when.isAfter(since))
                .min(Instant::compareTo);
    }

    /** {@inheritDoc} */
    @Override
    public void clear(String attemptKey) {
        attempts.remove(attemptKey);
    }

    /** {@inheritDoc} */
    @Override
    public int purgeOlderThan(Instant cutoff) {
        int removed = 0;
        for (Map.Entry<String, List<Instant>> entry : attempts.entrySet()) {
            List<Instant> kept = entry.getValue().stream()
                    .filter(when -> !when.isBefore(cutoff))
                    .toList();
            removed += entry.getValue().size() - kept.size();
            if (kept.isEmpty()) {
                attempts.remove(entry.getKey());
            } else {
                attempts.put(entry.getKey(), new CopyOnWriteArrayList<>(kept));
            }
        }
        return removed;
    }
}
