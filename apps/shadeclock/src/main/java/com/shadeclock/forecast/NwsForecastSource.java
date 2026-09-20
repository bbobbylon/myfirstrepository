package com.shadeclock.forecast;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shadeclock.heat.HeatIndexCalculator;
import com.shadeclock.heat.HourlyConditions;

/**
 * Fetches hourly forecasts from the US National Weather Service API.
 *
 * <p>Active when {@code shadeclock.forecast-source=nws}. The default is the fixture, so a
 * misconfigured deployment fails towards obviously synthetic data rather than towards
 * silently broken live data.
 *
 * <h2>How api.weather.gov works</h2>
 * It is a two-step lookup, which is the part most first implementations get wrong:
 * <ol>
 *   <li>{@code GET /points/{lat},{lon}} returns metadata including a
 *       {@code properties.forecastHourly} URL specific to that grid square.</li>
 *   <li>{@code GET} that URL returns {@code properties.periods[]}, each with
 *       {@code startTime}, {@code temperature}, {@code temperatureUnit} and
 *       {@code relativeHumidity.value}.</li>
 * </ol>
 *
 * <p>The API is free and needs no key. It does ask callers to send an identifying
 * {@code User-Agent}; requests without one may be rejected.
 *
 * <p><b>⚠️ Never exercised against the live API.</b> The build environment's egress policy
 * blocks {@code api.weather.gov}, so this class is written from the published documentation
 * and tested only against recorded shapes. Treat the first live run as part of the work:
 * confirm the field names, the units, and whether humidity is ever absent.
 *
 * @see <a href="https://weather-gov.github.io/api/">api.weather.gov documentation</a>
 */
@Component
@ConditionalOnProperty(name = "shadeclock.forecast-source", havingValue = "nws")
public class NwsForecastSource implements ForecastSource {

    private static final Logger log = LoggerFactory.getLogger(NwsForecastSource.class);

    private final RestClient restClient;
    private final HeatIndexCalculator calculator;
    private final String baseUrl;

    /**
     * @param restClientBuilder Spring's preconfigured builder
     * @param calculator        derives heat index from the returned readings
     * @param baseUrl           API base, overridable so tests can point at a stub
     * @param userAgent         identifying contact string required by NWS
     */
    public NwsForecastSource(RestClient.Builder restClientBuilder,
                             HeatIndexCalculator calculator,
                             @Value("${shadeclock.nws.base-url:https://api.weather.gov}") String baseUrl,
                             @Value("${shadeclock.nws.user-agent:(shadeclock, contact@example.com)}")
                             String userAgent) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", "application/geo+json")
                .build();
        this.calculator = calculator;
        this.baseUrl = baseUrl;
    }

    /** {@inheritDoc} */
    @Override
    public List<HourlyConditions> hourlyForecast(double latitude, double longitude, LocalDate date) {
        String hourlyUrl = resolveHourlyForecastUrl(latitude, longitude);

        HourlyForecastResponse forecast;
        try {
            forecast = restClient.get().uri(hourlyUrl).retrieve().body(HourlyForecastResponse.class);
        } catch (RestClientException e) {
            throw new ForecastFetchException("NWS hourly forecast request failed: " + hourlyUrl, e);
        }

        if (forecast == null || forecast.properties == null || forecast.properties.periods == null) {
            throw new ForecastFetchException("NWS returned no forecast periods for " + hourlyUrl);
        }

        List<HourlyConditions> hours = new ArrayList<>();
        for (HourlyForecastResponse.Period period : forecast.properties.periods) {
            HourlyConditions converted = toConditions(period, date);
            if (converted != null) {
                hours.add(converted);
            }
        }

        log.info("Fetched {} forecast hours for {} on {}", hours.size(), hourlyUrl, date);
        return hours;
    }

    /** {@inheritDoc} */
    @Override
    public String describeSource() {
        return "US National Weather Service (api.weather.gov)";
    }

    /**
     * Performs step 1 of the lookup: coordinates to a grid-specific hourly forecast URL.
     *
     * @param latitude  site latitude
     * @param longitude site longitude
     * @return the hourly forecast URL
     * @throws ForecastFetchException if the lookup fails or returns no URL
     */
    private String resolveHourlyForecastUrl(double latitude, double longitude) {
        String pointsUrl = "%s/points/%.4f,%.4f".formatted(baseUrl, latitude, longitude);
        try {
            PointsResponse points =
                    restClient.get().uri(pointsUrl).retrieve().body(PointsResponse.class);

            if (points == null || points.properties == null
                    || points.properties.forecastHourly == null) {
                throw new ForecastFetchException(
                        "NWS /points returned no forecastHourly URL for " + pointsUrl);
            }
            return points.properties.forecastHourly;
        } catch (RestClientException e) {
            throw new ForecastFetchException("NWS /points request failed: " + pointsUrl, e);
        }
    }

    /**
     * Converts one NWS period into domain conditions, or {@code null} if it is unusable.
     *
     * <p>Returns {@code null} rather than throwing for periods on other days, or periods
     * missing humidity: one unusable hour should not abort a whole day's plan.
     *
     * @param period the API period
     * @param date   the day being planned
     * @return the converted conditions, or {@code null} to skip this period
     */
    private HourlyConditions toConditions(HourlyForecastResponse.Period period, LocalDate date) {
        if (period.startTime == null || period.temperature == null) {
            return null;
        }

        OffsetDateTime start;
        try {
            start = OffsetDateTime.parse(period.startTime);
        } catch (RuntimeException e) {
            log.warn("Unparseable NWS startTime '{}' - skipping this hour", period.startTime);
            return null;
        }

        if (!start.toLocalDate().equals(date)) {
            return null;
        }

        // The API can return Celsius; convert rather than assume Fahrenheit. Assuming the
        // unit would produce a plausible-looking but badly wrong schedule, which is worse
        // than an obvious failure.
        double tempF = "C".equalsIgnoreCase(period.temperatureUnit)
                ? (period.temperature * 9.0 / 5.0) + 32.0
                : period.temperature;

        if (period.relativeHumidity == null || period.relativeHumidity.value == null) {
            log.warn("NWS period at {} has no humidity - skipping this hour", period.startTime);
            return null;
        }

        return HourlyConditions.from(
                start.toLocalDateTime(), tempF, period.relativeHumidity.value, calculator);
    }

    /** Binding for the {@code /points} response. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PointsResponse {
        @JsonProperty("properties")
        public Properties properties;

        /** The subset of point metadata we use. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Properties {
            /** Grid-specific URL for the hourly forecast. */
            @JsonProperty("forecastHourly")
            public String forecastHourly;
        }
    }

    /** Binding for the hourly forecast response. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class HourlyForecastResponse {
        @JsonProperty("properties")
        public Properties properties;

        /** Wrapper holding the period list. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Properties {
            @JsonProperty("periods")
            public List<Period> periods;
        }

        /** One forecast hour. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Period {
            /** ISO-8601 start instant with offset. */
            @JsonProperty("startTime")
            public String startTime;

            /** Temperature value in {@link #temperatureUnit}. */
            @JsonProperty("temperature")
            public Double temperature;

            /** {@code "F"} or {@code "C"}. */
            @JsonProperty("temperatureUnit")
            public String temperatureUnit;

            /** Humidity, nested as a value/unitCode pair. */
            @JsonProperty("relativeHumidity")
            public QuantitativeValue relativeHumidity;
        }

        /** NWS wraps scalar measurements in a value/unit object. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class QuantitativeValue {
            @JsonProperty("value")
            public Double value;
        }
    }
}
