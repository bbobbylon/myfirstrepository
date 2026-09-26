package com.shadeclock.heat;

/**
 * The National Weather Service heat index risk categories.
 *
 * <p>These are the bands printed on the standard NWS heat index chart. They describe
 * <em>general population</em> risk, which is an important caveat: an outdoor worker doing
 * sustained physical labour in direct sun is under far more thermal load than the sedentary
 * shaded person the chart assumes. ShadeClock therefore uses these bands for
 * <b>communication</b> - a familiar word a foreman recognises - and uses the jurisdiction
 * rulesets in {@code com.shadeclock.rules} for <b>decisions</b>.
 *
 * <p>⚠️ The numeric boundaries below come from the published NWS chart as reported in
 * secondary sources; the build environment could not reach weather.gov to read the chart
 * directly. Verify against the NWS chart before relying on the exact cut points.
 *
 * @see <a href="https://www.weather.gov/safety/heat-index">NWS Heat Index</a>
 */
public enum HeatRiskBand {

    /** Below 80°F. No heat-specific precautions indicated by the chart. */
    NONE("No elevated heat risk indicated"),

    /** 80-90°F. Fatigue possible with prolonged exposure and activity. */
    CAUTION("Fatigue possible with prolonged exposure and activity"),

    /** 90-103°F. Heat exhaustion possible; heat stroke possible with prolonged exertion. */
    EXTREME_CAUTION("Heat exhaustion possible with prolonged exposure and activity"),

    /** 103-124°F. Heat exhaustion likely; heat stroke likely with continued activity. */
    DANGER("Heat exhaustion likely; heat stroke possible with continued activity"),

    /** 125°F and above. Heat stroke highly likely. */
    EXTREME_DANGER("Heat stroke highly likely");

    private final String description;

    HeatRiskBand(String description) {
        this.description = description;
    }

    /**
     * A short plain-language description of what this band means.
     *
     * @return the description, never {@code null}
     */
    public String description() {
        return description;
    }

    /**
     * Classifies a heat index value into a band.
     *
     * @param heatIndexF heat index in degrees Fahrenheit
     * @return the matching band, never {@code null}
     */
    public static HeatRiskBand forHeatIndex(double heatIndexF) {
        if (heatIndexF >= 125) {
            return EXTREME_DANGER;
        }
        if (heatIndexF >= 103) {
            return DANGER;
        }
        if (heatIndexF >= 90) {
            return EXTREME_CAUTION;
        }
        if (heatIndexF >= 80) {
            return CAUTION;
        }
        return NONE;
    }
}
