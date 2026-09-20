package com.shadeclock.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shadeclock.heat.HeatIndexCalculator;
import com.shadeclock.heat.HeatRiskBand;
import com.shadeclock.heat.HourlyConditions;

/**
 * Tests that the offline forecast fixture produces a usable, deterministic day.
 *
 * <p>This is the test proving ShadeClock can be developed and verified with no network at
 * all - which was not optional here, since the build environment blocks
 * {@code api.weather.gov}.
 */
class FixtureForecastSourceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 15);

    private final FixtureForecastSource source =
            new FixtureForecastSource(new HeatIndexCalculator());

    @Test
    @DisplayName("produces a full working day of hours")
    void producesAFullDay() {
        List<HourlyConditions> hours = source.hourlyForecast(34.05, -118.24, DAY);

        assertThat(hours).hasSize(15);
        assertThat(hours).extracting(HourlyConditions::hour).isSorted();
        assertThat(hours.getFirst().hour().getHour()).isEqualTo(5);
    }

    @Test
    @DisplayName("is deterministic - the same inputs always give the same day")
    void isDeterministic() {
        // Randomised fixtures produce tests that fail once a fortnight for reasons nobody
        // can reconstruct afterwards.
        assertThat(source.hourlyForecast(34.05, -118.24, DAY))
                .isEqualTo(source.hourlyForecast(34.05, -118.24, DAY));
    }

    @Test
    @DisplayName("follows a realistic diurnal curve, peaking in the afternoon")
    void followsDiurnalCurve() {
        List<HourlyConditions> hours = source.hourlyForecast(34.05, -118.24, DAY);

        HourlyConditions peak = hours.stream()
                .max(java.util.Comparator.comparingDouble(HourlyConditions::heatIndexF))
                .orElseThrow();

        assertThat(peak.hour().getHour()).isBetween(12, 17);
        assertThat(hours.getFirst().heatIndexF()).isLessThan(peak.heatIndexF());
        assertThat(hours.getLast().heatIndexF()).isLessThan(peak.heatIndexF());
    }

    @Test
    @DisplayName("crosses the rule thresholds the scheduler needs to exercise")
    void crossesRuleThresholds() {
        // The fixture earns its keep by spanning bands a single real day might not.
        List<HeatRiskBand> bands = source.hourlyForecast(34.05, -118.24, DAY).stream()
                .map(HourlyConditions::riskBand).distinct().toList();

        assertThat(bands).contains(HeatRiskBand.CAUTION, HeatRiskBand.EXTREME_CAUTION,
                HeatRiskBand.DANGER);
    }

    @Test
    @DisplayName("labels itself as not live, so a misconfiguration is visible")
    void labelsItselfAsNotLive() {
        assertThat(source.describeSource()).contains("NOT LIVE");
    }

    @Test
    @DisplayName("heat index and risk band always agree with each other")
    void heatIndexAndBandAgree() {
        // The factory exists to make these impossible to desynchronise; this asserts it.
        assertThat(source.hourlyForecast(34.05, -118.24, DAY)).allSatisfy(hour ->
                assertThat(hour.riskBand())
                        .isEqualTo(HeatRiskBand.forHeatIndex(hour.heatIndexF())));
    }
}
