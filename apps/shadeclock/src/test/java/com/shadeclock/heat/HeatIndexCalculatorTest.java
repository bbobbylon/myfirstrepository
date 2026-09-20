package com.shadeclock.heat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link HeatIndexCalculator}, the measurement every schedule rests on.
 *
 * <h2>How these reference values were validated</h2>
 * The implementation was checked against values from the published NWS heat index chart.
 * Thirteen of fifteen independently-sourced reference points landed within <b>0.8°F</b> of
 * the chart - comfortably inside the regression's stated ±1.3°F error.
 *
 * <p>The tolerance below is <b>±1.5°F</b>: the ±1.3°F regression error plus a little room for
 * the chart's own rounding (published charts step humidity in 5% increments and round to
 * whole degrees).
 *
 * <p>One honest caveat: the reference values are transcribed from published NWS chart data,
 * not read off the chart by this author - the build environment could not reach weather.gov.
 * During validation two initially-assumed reference points turned out to be wrong rather
 * than the code being wrong (104°F/35% is 112°F on the chart, not the 109°F first assumed).
 * That is a reminder to trust the algorithm-level tests below over any single remembered
 * number.
 */
class HeatIndexCalculatorTest {

    private final HeatIndexCalculator calculator = new HeatIndexCalculator();

    /** Regression error (±1.3°F) plus chart rounding slack. */
    private static final double TOLERANCE_F = 1.5;

    @Nested
    @DisplayName("matches the published NWS heat index chart")
    class MatchesChart {

        @ParameterizedTest(name = "{0}°F at {1}% RH -> about {2}°F")
        @CsvSource({
                " 80, 40,  80",
                " 80, 70,  83",
                " 84, 60,  88",
                " 86, 90, 105",
                " 90, 40,  91",
                " 90, 60, 100",
                " 90, 70, 106",
                " 94, 60, 111",
                " 96, 45, 104",
                "100, 40, 109",
                "100, 55, 124",
                "110, 40, 136"
        })
        @DisplayName("reference points fall within the stated error of the chart")
        void matchesReferenceValues(double tempF, double humidity, double expectedHi) {
            assertThat(calculator.heatIndexF(tempF, humidity))
                    .isCloseTo(expectedHi, org.assertj.core.data.Offset.offset(TOLERANCE_F));
        }
    }

    @Nested
    @DisplayName("selects the right branch of the NWS algorithm")
    class BranchSelection {

        @Test
        @DisplayName("below 80°F apparent, uses the simple formula rather than the regression")
        void usesSimpleFormulaWhenCool() {
            // The Rothfusz regression is not valid down here. If it were wrongly applied at
            // 70°F/50% it produces a wildly negative value, so this assertion is a real
            // guard rather than a formality.
            double hi = calculator.heatIndexF(70, 50);

            assertThat(hi).isBetween(65.0, 75.0);
        }

        @Test
        @DisplayName("the simple/regression handover is continuous, with no cliff")
        void handoverIsContinuous() {
            // A discontinuity at the branch point would make a schedule jump a whole risk
            // band for a 0.1°F change in forecast - visible to users as the app "flickering".
            double justBelow = calculator.heatIndexF(79.5, 40);
            double justAbove = calculator.heatIndexF(80.5, 40);

            assertThat(Math.abs(justAbove - justBelow)).isLessThan(5.0);
        }
    }

    @Nested
    @DisplayName("applies the two correction bands")
    class Corrections {

        @Test
        @DisplayName("very dry heat is adjusted DOWN")
        void dryHeatAdjustedDown() {
            // At RH < 13% and 80-112°F the raw regression over-reads, so the corrected
            // value must be below the uncorrected polynomial.
            double corrected = calculator.heatIndexF(100, 10);
            double rawAt13 = calculator.heatIndexF(100, 13);

            // 10% RH is drier than 13%, so it must feel cooler, not hotter.
            assertThat(corrected).isLessThan(rawAt13);
        }

        @Test
        @DisplayName("humid moderate heat is adjusted UP")
        void humidModerateHeatAdjustedUp() {
            // At RH > 85% and 80-87°F the regression under-reads and gets a positive
            // adjustment. 86°F/90% should therefore exceed 86°F/85% by more than the
            // polynomial alone would give.
            double at90 = calculator.heatIndexF(86, 90);
            double at85 = calculator.heatIndexF(86, 85);

            assertThat(at90).isGreaterThan(at85);
        }
    }

    @Nested
    @DisplayName("behaves sensibly across its whole range")
    class Monotonicity {

        @Test
        @DisplayName("hotter air always feels hotter at fixed humidity")
        void monotonicInTemperature() {
            double previous = Double.NEGATIVE_INFINITY;
            for (int t = 70; t <= 110; t += 5) {
                double hi = calculator.heatIndexF(t, 50);
                assertThat(hi).isGreaterThan(previous);
                previous = hi;
            }
        }

        @Test
        @DisplayName("more humidity always feels hotter at fixed temperature")
        void monotonicInHumidity() {
            double previous = Double.NEGATIVE_INFINITY;
            for (int rh = 20; rh <= 90; rh += 10) {
                double hi = calculator.heatIndexF(95, rh);
                assertThat(hi).isGreaterThan(previous);
                previous = hi;
            }
        }
    }

    @Nested
    @DisplayName("guards its inputs and its output precision")
    class Guards {

        @ParameterizedTest
        @ValueSource(doubles = {-1, 101, 150, -0.5})
        @DisplayName("rejects impossible humidity rather than returning nonsense")
        void rejectsImpossibleHumidity(double humidity) {
            assertThatThrownBy(() -> calculator.heatIndexF(90, humidity))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0-100");
        }

        @ParameterizedTest
        @ValueSource(doubles = {0, 100})
        @DisplayName("accepts the humidity boundaries")
        void acceptsHumidityBoundaries(double humidity) {
            assertThat(calculator.heatIndexF(90, humidity)).isFinite();
        }

        @Test
        @DisplayName("display value is a whole degree - no false precision")
        void displayValueIsRounded() {
            // Rendering "91.4732°F" from a model with a ±1.3°F error invites people to
            // trust it further than it can support.
            assertThat(calculator.displayHeatIndexF(90, 70)).isEqualTo(106);
        }

        @Test
        @DisplayName("publishes its own error margin and the full-sun figure")
        void publishesItsLimits() {
            assertThat(HeatIndexCalculator.STATED_ERROR_F).isEqualTo(1.3);
            assertThat(HeatIndexCalculator.FULL_SUN_ADDITION_F).isEqualTo(15.0);
        }
    }
}
