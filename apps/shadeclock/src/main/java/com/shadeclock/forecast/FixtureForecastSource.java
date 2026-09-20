package com.shadeclock.forecast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.shadeclock.heat.HeatIndexCalculator;
import com.shadeclock.heat.HourlyConditions;

/**
 * Generates a deterministic synthetic summer day instead of calling a weather service.
 *
 * <p>Active when {@code shadeclock.forecast-source=fixture}, which is the default. See
 * {@link ForecastSource} for why this seam exists.
 *
 * <p>The shape is a realistic diurnal curve - cool at dawn, peaking mid-afternoon, falling
 * into the evening - built from a fixed table rather than random numbers so that every test
 * assertion is reproducible. Randomised fixtures produce tests that fail once a fortnight
 * for reasons nobody can reconstruct.
 */
@Component
@ConditionalOnProperty(name = "shadeclock.forecast-source", havingValue = "fixture",
        matchIfMissing = true)
public class FixtureForecastSource implements ForecastSource {

    /**
     * Hourly air temperature in °F for hours 05:00-19:00.
     *
     * <p>Deliberately crosses several rule thresholds: it starts below 80°F, passes the
     * 80°F shade trigger, crosses 95°F high-heat, and peaks above 103°F so the Danger band
     * and its longer rest cadence are exercised.
     */
    private static final double[] TEMPERATURES_F = {
            72, 75, 79, 83, 87, 91, 95, 99, 102, 104, 103, 100, 95, 89, 83
    };

    /** Matching relative humidity, falling as the day heats up, as real days do. */
    private static final double[] HUMIDITY_PERCENT = {
            70, 66, 60, 54, 48, 43, 39, 35, 32, 30, 31, 35, 42, 52, 60
    };

    /** The first hour in the tables above. */
    private static final int FIRST_HOUR = 5;

    private final HeatIndexCalculator calculator;

    /**
     * @param calculator used to derive heat index from the synthetic readings
     */
    public FixtureForecastSource(HeatIndexCalculator calculator) {
        this.calculator = calculator;
    }

    /** {@inheritDoc} */
    @Override
    public List<HourlyConditions> hourlyForecast(double latitude, double longitude, LocalDate date) {
        List<HourlyConditions> hours = new ArrayList<>(TEMPERATURES_F.length);
        for (int i = 0; i < TEMPERATURES_F.length; i++) {
            LocalDateTime hour = date.atStartOfDay().plusHours(FIRST_HOUR + i);
            hours.add(HourlyConditions.from(
                    hour, TEMPERATURES_F[i], HUMIDITY_PERCENT[i], calculator));
        }
        return hours;
    }

    /** {@inheritDoc} */
    @Override
    public String describeSource() {
        // Says "NOT LIVE" outright so a misconfigured deployment is obvious on screen
        // rather than quietly scheduling a real crew off synthetic weather.
        return "Synthetic sample forecast (NOT LIVE - development fixture)";
    }
}
