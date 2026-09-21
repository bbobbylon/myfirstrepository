package com.shadeclock.heat;

import org.springframework.stereotype.Component;

/**
 * Computes the US National Weather Service heat index ("apparent temperature").
 *
 * <p>Everything else rests on this: if it is wrong, every break schedule is wrong. So it is
 * implemented directly from the published NWS algorithm and tested against chart values.
 *
 * <p>Algorithm: compute {@code HIs = 0.5 * (T + 61.0 + (T - 68.0) * 1.2 + RH * 0.094)};
 * if {@code (HIs + T) / 2 < 80°F} that simple value is the answer, because the regression is
 * not valid below there. Otherwise apply the <b>Rothfusz regression</b> (NWS Technical
 * Attachment SR 90-23, 1990) plus two correction bands where it misbehaves - a subtraction
 * in very dry heat, an addition in humid moderate heat.
 *
 * <p>⚠️ Two limits that must never be hidden from users: the <b>stated error is ±1.3°F</b>,
 * so no heat index is exact; and it <b>assumes shade and light wind</b>, while NWS states
 * full sun can add <b>up to 15°F</b> - a "safe" 88°F may really be 103°F, the Danger band.
 * A screening tool, not a substitute for on-site WBGT.
 *
 * @see <a href="https://www.wpc.ncep.noaa.gov/heat_index/details_hi.html">NWS WPC -
 *      Calculating the Heat Index</a>
 * @see <a href="https://www.weather.gov/media/ffc/ta_htindx.PDF">NWS Technical Attachment
 *      SR 90-23, Rothfusz (1990)</a>
 */
@Component
public class HeatIndexCalculator {

    /**
     * The regression's stated accuracy, in degrees Fahrenheit.
     *
     * <p>Exposed as a constant rather than buried in a comment so that user-facing code can
     * quote the uncertainty instead of implying precision the method does not have.
     */
    public static final double STATED_ERROR_F = 1.3;

    /** Below this apparent temperature the Rothfusz regression is not applicable. */
    private static final double ROTHFUSZ_FLOOR_F = 80.0;

    /**
     * How much full sunshine can add to the real thermal load, in degrees Fahrenheit.
     *
     * <p>Published by NWS alongside the heat index chart, which is explicitly labelled as
     * applying to shady locations. Exposed as a constant so user-facing code can state the
     * figure rather than gesture vaguely at "conditions may be worse in sun".
     */
    public static final double FULL_SUN_ADDITION_F = 15.0;

    /**
     * Computes the heat index for an air temperature and relative humidity.
     *
     * @param temperatureF air temperature in degrees Fahrenheit
     * @param relativeHumidityPercent relative humidity, 0-100
     * @return the heat index in degrees Fahrenheit
     * @throws IllegalArgumentException if humidity is outside 0-100, which would silently
     *         produce nonsense rather than fail
     */
    public double heatIndexF(double temperatureF, double relativeHumidityPercent) {
        if (relativeHumidityPercent < 0 || relativeHumidityPercent > 100) {
            throw new IllegalArgumentException(
                    "Relative humidity must be 0-100, got " + relativeHumidityPercent);
        }

        double t = temperatureF;
        double rh = relativeHumidityPercent;

        // Step 1-2: the simple formula, averaged with air temperature.
        double simple = 0.5 * (t + 61.0 + ((t - 68.0) * 1.2) + (rh * 0.094));
        if (((simple + t) / 2.0) < ROTHFUSZ_FLOOR_F) {
            return simple;
        }

        // Step 3: Rothfusz regression.
        double hi = -42.379
                + 2.04901523 * t
                + 10.14333127 * rh
                - 0.22475541 * t * rh
                - 0.00683783 * t * t
                - 0.05481717 * rh * rh
                + 0.00122874 * t * t * rh
                + 0.00085282 * t * rh * rh
                - 0.00000199 * t * t * rh * rh;

        // Step 4a: very dry heat - the regression over-reads, so subtract.
        if (rh < 13 && t >= 80 && t <= 112) {
            hi -= ((13 - rh) / 4.0) * Math.sqrt((17 - Math.abs(t - 95.0)) / 17.0);
        }

        // Step 4b: humid moderate heat - the regression under-reads, so add.
        if (rh > 85 && t >= 80 && t <= 87) {
            hi += ((rh - 85) / 10.0) * ((87 - t) / 5.0);
        }

        return hi;
    }

    /**
     * Computes the heat index and rounds it the way it should be shown to a person.
     *
     * <p>Rounded to a whole degree deliberately. Rendering "91.4732°F" from a model with a
     * ±1.3°F error is false precision, and false precision in a safety tool invites people
     * to trust it past what it can support.
     *
     * @param temperatureF air temperature in degrees Fahrenheit
     * @param relativeHumidityPercent relative humidity, 0-100
     * @return the heat index rounded to the nearest whole degree Fahrenheit
     */
    public int displayHeatIndexF(double temperatureF, double relativeHumidityPercent) {
        return (int) Math.round(heatIndexF(temperatureF, relativeHumidityPercent));
    }
}
