package com.shadeclock.heat;

import org.springframework.stereotype.Component;

/**
 * Computes the US National Weather Service heat index ("apparent temperature").
 *
 * <p>This is the measurement at the bottom of everything else ShadeClock does. If it is
 * wrong, every break schedule built on it is wrong, so it is implemented directly from the
 * published NWS algorithm and tested against known reference values rather than
 * approximated.
 *
 * <h2>The algorithm, exactly as NWS specifies it</h2>
 * <ol>
 *   <li>Compute a simple formula first:
 *       {@code HIs = 0.5 * (T + 61.0 + (T - 68.0) * 1.2 + RH * 0.094)}</li>
 *   <li>Average that with the air temperature. If {@code (HIs + T) / 2 < 80°F}, the simple
 *       value is the answer and we stop. The regression below is not valid down there.</li>
 *   <li>Otherwise apply the <b>Rothfusz regression</b>, a multiple-regression fit described
 *       in NWS Technical Attachment SR 90-23 (1990).</li>
 *   <li>Apply two correction bands where the regression is known to misbehave:
 *       a subtraction in very dry heat, an addition in humid moderate heat.</li>
 * </ol>
 *
 * <h2>Two limits that must never be hidden from users</h2>
 * <ul>
 *   <li><b>The regression carries a stated error of ±1.3°F.</b> A heat index is therefore
 *       never presented as an exact figure anywhere in this application.</li>
 *   <li><b>Heat index assumes shade and light wind. It does not model direct sun or
 *       radiant heat</b> from machinery, asphalt or fires. NWS states plainly that
 *       <b>exposure to full sunshine can increase heat index values by up to 15°F</b> -
 *       and full sun is exactly the condition most outdoor crews work in. A crew at a
 *       "safe" 88°F indicated may really be at 103°F, which is the NWS Danger band. This
 *       makes ShadeClock a <em>screening</em> tool, not a substitute for WBGT (wet bulb
 *       globe temperature) measurement on site.</li>
 * </ul>
 *
 * <p><b>The analogy.</b> Heat index is a <em>weather forecast for your body</em>: it tells
 * you what the air is likely to do to a person standing in the shade. WBGT is a
 * <em>thermometer held where the person actually is</em> - it accounts for sunlight, wind
 * and radiant sources. The forecast is free and available everywhere; the thermometer is
 * accurate but needs a device on site. v0.1 uses the forecast, and says so plainly.
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
