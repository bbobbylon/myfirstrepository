package com.shadeclock.forecast;

import java.time.LocalDate;
import java.util.List;

import com.shadeclock.heat.HourlyConditions;

/**
 * Supplies hourly weather for a work site.
 *
 * <p>The same fixture/live seam as RefillRadar's {@code ShortageSource}, for the same two
 * reasons: this build environment's egress policy blocks {@code api.weather.gov}, and even
 * with open network access a test suite that depends on a third-party weather service is
 * slow, rate-limited, and red whenever someone else has an outage.
 *
 * <p>The fixture also lets tests exercise conditions you cannot order up from the real
 * atmosphere - a 118°F afternoon, a humidity spike, a flat 70°F day - which is where the
 * scheduling logic most needs checking.
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
