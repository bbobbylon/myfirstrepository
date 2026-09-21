package com.shadeclock.forecast;

import java.time.LocalDate;
import java.util.List;

import com.shadeclock.heat.HourlyConditions;

/**
 * Supplies hourly weather for a work site.
 *
 * <p>The same fixture/live seam as RefillRadar's {@code ShortageSource}: this environment
 * blocks {@code api.weather.gov}, and a suite depending on a third-party weather service is
 * slow and red whenever someone else has an outage. The fixture also exercises conditions
 * you cannot order from the real atmosphere - a 118°F afternoon, a flat 70°F day.
 */
public interface ForecastSource {

    /**
     * Fetches hourly conditions for a location.
     *
     * @param latitude  site latitude
     * @param longitude site longitude
     * @param date      the day to fetch
     * @return hourly conditions, never {@code null}; empty if the source has nothing
     * @throws ForecastFetchException if the forecast could not be retrieved or parsed
     */
    List<HourlyConditions> hourlyForecast(double latitude, double longitude, LocalDate date);

    /**
     * A short label identifying where this data came from.
     *
     * <p>Shown on every schedule. A plan that tells a crew when to stop working owes them
     * the provenance of the weather it is based on, and a deployment accidentally running on
     * fixtures must be visibly distinguishable from one running on real forecasts.
     *
     * @return a human-readable source description, never {@code null}
     */
    String describeSource();
}
