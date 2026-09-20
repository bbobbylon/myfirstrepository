package com.shadeclock.crew;

import java.util.List;

/**
 * A work crew at one site, which is the unit ShadeClock schedules for.
 *
 * <h2>Why the crew, not the individual, is the unit</h2>
 * The official OSHA-NIOSH Heat Safety Tool is a single-worker app: one person, one phone,
 * one risk colour. But the decision that actually prevents heat illness - "we stop at 11:40
 * and rotate in two groups" - is made by a supervisor for a group, not by each worker for
 * themselves. Building for the individual puts the information in the wrong hands.
 *
 * @param id        stable identifier
 * @param name      what the supervisor calls this crew
 * @param siteLabel a human-readable site description, e.g. {@code "Route 12 resurfacing"}
 * @param latitude  site latitude, used to fetch the forecast
 * @param longitude site longitude, used to fetch the forecast
 * @param jurisdiction ruleset key for the state whose rules apply, e.g. {@code "CA"}
 * @param workers   crew members; never {@code null}, may be empty
 */
public record Crew(
        String id,
        String name,
        String siteLabel,
        double latitude,
        double longitude,
        String jurisdiction,
        List<Worker> workers) {

    /**
     * Defensive-copies the worker list so a caller cannot mutate a crew after construction.
     */
    public Crew {
        workers = workers == null ? List.of() : List.copyOf(workers);
    }

    /**
     * The workers who need extra precautions on a given day.
     *
     * <p>Surfaced separately because this is the list a supervisor should read out at the
     * morning briefing: not "the crew is at risk" but "these three people specifically".
     *
     * @param today the day to assess
     * @return workers whose adaptation is incomplete, never {@code null}
     */
    public List<Worker> workersNeedingExtraPrecautions(java.time.LocalDate today) {
        return workers.stream()
                .filter(worker -> worker.acclimatizationOn(today).needsExtraPrecautions())
                .toList();
    }
}
