package com.shadeclock.heat;

import java.time.LocalDateTime;

/**
 * Weather conditions for one hour at one work site, with the derived heat index.
 *
 * <p>An immutable record - the same "printed receipt, not whiteboard" reasoning as
 * RefillRadar's {@code ShortageRecord}. Forecast data arrives from outside this application
 * and should not be quietly editable after the fact.
 *
 * @param hour              the local hour these conditions apply to
 * @param temperatureF      air temperature in degrees Fahrenheit
 * @param relativeHumidity  relative humidity, 0-100
 * @param heatIndexF        derived apparent temperature in degrees Fahrenheit
 * @param riskBand          the NWS band this heat index falls in
 */
public record HourlyConditions(
        LocalDateTime hour,
        double temperatureF,
        double relativeHumidity,
        double heatIndexF,
        HeatRiskBand riskBand) {

    /**
     * Builds an {@code HourlyConditions} from raw forecast values, deriving the heat index.
     *
     * <p>A factory rather than a constructor so that the heat index and the risk band can
     * never disagree with the temperature and humidity they were supposedly derived from.
     * Letting a caller pass all five fields independently would allow exactly that.
     *
     * @param hour             the local hour
     * @param temperatureF     air temperature in degrees Fahrenheit
     * @param relativeHumidity relative humidity, 0-100
     * @param calculator       the heat index calculator to use
     * @return a populated record, never {@code null}
     */
    public static HourlyConditions from(LocalDateTime hour,
                                        double temperatureF,
                                        double relativeHumidity,
                                        HeatIndexCalculator calculator) {
        double heatIndex = calculator.heatIndexF(temperatureF, relativeHumidity);
        return new HourlyConditions(
                hour, temperatureF, relativeHumidity, heatIndex,
                HeatRiskBand.forHeatIndex(heatIndex));
    }

    /**
     * The heat index rounded for display.
     *
     * @return the heat index to the nearest whole degree Fahrenheit
     */
    public int displayHeatIndexF() {
        return (int) Math.round(heatIndexF);
    }
}
