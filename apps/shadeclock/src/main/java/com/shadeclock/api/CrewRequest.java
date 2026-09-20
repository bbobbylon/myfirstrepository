package com.shadeclock.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Incoming payload for creating a crew.
 *
 * <p>Separate from {@link com.shadeclock.crew.Crew} so clients cannot set fields the domain
 * generates, and so validation happens at the edge with a {@code 400} rather than deep in
 * the scheduler.
 *
 * @param name         what the supervisor calls this crew
 * @param siteLabel    human-readable site description
 * @param latitude     site latitude, -90 to 90
 * @param longitude    site longitude, -180 to 180
 * @param jurisdiction state code whose rules apply, e.g. {@code "CA"}
 * @param workers      crew members
 */
public record CrewRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "siteLabel is required")
        String siteLabel,

        @NotNull(message = "latitude is required")
        @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "longitude is required")
        @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
        Double longitude,

        String jurisdiction,

        List<WorkerRequest> workers) {

    /**
     * One crew member as supplied by a client.
     *
     * @param name               the worker's name
     * @param heatWorkStartedOn  first day of the current run of work in heat
     * @param lastAbsenceEndedOn day a break of a week or more ended, or {@code null}
     */
    public record WorkerRequest(
            @NotBlank(message = "worker name is required")
            String name,

            @NotNull(message = "heatWorkStartedOn is required (format: YYYY-MM-DD)")
            LocalDate heatWorkStartedOn,

            LocalDate lastAbsenceEndedOn) {
    }
}
