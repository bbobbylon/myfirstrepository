package com.shadeclock.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shadeclock.crew.Crew;
import com.shadeclock.crew.Worker;
import com.shadeclock.heat.HeatIndexCalculator;
import com.shadeclock.heat.HourlyConditions;
import com.shadeclock.rules.CaliforniaHeatRuleset;
import com.shadeclock.rules.FederalBaselineRuleset;
import com.shadeclock.rules.RulesetRegistry;

/**
 * Tests for the scheduling engine - the class that turns weather into a timetable.
 *
 * <p>Runs entirely in memory: no network, no database, no clock of its own. The whole suite
 * executes in milliseconds, which is what makes it something you actually run on every
 * change rather than skip because it is slow.
 */
class ScheduleBuilderTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 15);

    private final HeatIndexCalculator calculator = new HeatIndexCalculator();
    private final ScheduleBuilder builder = new ScheduleBuilder(
            new RulesetRegistry(List.of(new CaliforniaHeatRuleset(), new FederalBaselineRuleset())));

    private Crew crew(String jurisdiction, Worker... workers) {
        return new Crew("c1", "Paving Crew", "Route 12", 34.05, -118.24,
                jurisdiction, List.of(workers));
    }

    private Worker adaptedWorker() {
        return new Worker("w1", "Adapted", DAY.minusDays(60), null);
    }

    private List<HourlyConditions> hours(double... temperaturesF) {
        return java.util.stream.IntStream.range(0, temperaturesF.length)
                .mapToObj(i -> HourlyConditions.from(
                        DAY.atStartOfDay().plusHours(7 + i), temperaturesF[i], 45, calculator))
                .toList();
    }

    @Test
    @DisplayName("a cool day needs no heat breaks")
    void coolDayNeedsNoBreaks() {
        WorkRestSchedule schedule =
                builder.build(crew("CA", adaptedWorker()), DAY, hours(65, 68, 70, 72));

        assertThat(schedule.requiresBreaks()).isFalse();
        assertThat(schedule.totalRestMinutes()).isZero();
    }

    @Test
    @DisplayName("a hot day produces breaks at the end of each affected hour")
    void hotDayProducesBreaks() {
        WorkRestSchedule schedule =
                builder.build(crew("CA", adaptedWorker()), DAY, hours(85, 95, 100, 98));

        assertThat(schedule.requiresBreaks()).isTrue();

        ScheduledBreak first = schedule.breaks().getFirst();
        // Rest sits at the END of the hour: work 08:00-08:50, rest 08:50-09:00.
        assertThat(first.end().getMinute()).isZero();
        assertThat(first.durationMinutes()).isPositive();
    }

    @Test
    @DisplayName("hotter hours get longer breaks")
    void hotterHoursGetLongerBreaks() {
        WorkRestSchedule schedule =
                builder.build(crew("CA", adaptedWorker()), DAY, hours(96, 112));

        assertThat(schedule.breaks()).hasSize(2);
        assertThat(schedule.breaks().get(1).durationMinutes())
                .isGreaterThan(schedule.breaks().get(0).durationMinutes());
    }

    @Test
    @DisplayName("every break explains itself and cites its rule")
    void breaksExplainThemselves() {
        // A schedule that cannot say why it stopped work is one that gets overridden the
        // first time it is inconvenient.
        WorkRestSchedule schedule =
                builder.build(crew("CA", adaptedWorker()), DAY, hours(100));

        assertThat(schedule.breaks()).allSatisfy(scheduled -> {
            assertThat(scheduled.reason()).contains("Heat index");
            assertThat(scheduled.citation()).isNotBlank();
            assertThat(scheduled.heatIndexF()).isPositive();
        });
    }

    @Test
    @DisplayName("hours are sorted, whatever order the forecast arrived in")
    void sortsHoursRegardlessOfInput() {
        List<HourlyConditions> shuffled = List.of(
                HourlyConditions.from(DAY.atStartOfDay().plusHours(14), 100, 45, calculator),
                HourlyConditions.from(DAY.atStartOfDay().plusHours(8), 82, 45, calculator),
                HourlyConditions.from(DAY.atStartOfDay().plusHours(11), 94, 45, calculator));

        WorkRestSchedule schedule = builder.build(crew("CA", adaptedWorker()), DAY, shuffled);

        assertThat(schedule.hourlyConditions())
                .extracting(HourlyConditions::hour)
                .isSorted();
    }

    @Test
    @DisplayName("the peak is reported so a supervisor can plan the day around it")
    void reportsPeak() {
        WorkRestSchedule schedule =
                builder.build(crew("CA", adaptedWorker()), DAY, hours(80, 95, 105, 92));

        assertThat(schedule.peakHour()).isEqualTo(DAY.atStartOfDay().plusHours(9));
        assertThat(schedule.peakHeatIndexF()).isGreaterThan(100);
    }

    @Test
    @DisplayName("workers needing watching are named individually, not counted")
    void namesWorkersNeedingWatching() {
        Crew mixed = crew("CA",
                adaptedWorker(),
                new Worker("w2", "New Starter", DAY.minusDays(1), null));

        WorkRestSchedule schedule = builder.build(mixed, DAY, hours(100));

        assertThat(schedule.workersNeedingWatching()).extracting(Worker::name)
                .containsExactly("New Starter");
        // "Three workers are at risk" is a statistic. "Watch New Starter" is an instruction.
        assertThat(schedule.advisories())
                .anySatisfy(advisory -> assertThat(advisory).contains("New Starter"));
    }

    @Test
    @DisplayName("an unmodelled state says so instead of passing guidance off as law")
    void unmodelledStateIsFlagged() {
        WorkRestSchedule schedule = builder.build(crew("TX", adaptedWorker()), DAY, hours(100));

        assertThat(schedule.usedFallbackRuleset()).isTrue();
        assertThat(schedule.advisories())
                .anySatisfy(advisory -> assertThat(advisory).contains("NOT that state's law"));
    }

    @Test
    @DisplayName("every schedule carries the verification caveat and the full-sun warning")
    void everyScheduleCarriesItsCaveats() {
        WorkRestSchedule schedule = builder.build(crew("CA", adaptedWorker()), DAY, hours(100));

        assertThat(schedule.verificationCaveat()).isNotBlank();
        assertThat(schedule.advisories())
                .anySatisfy(advisory -> assertThat(advisory).contains("UNVERIFIED"));
        assertThat(schedule.advisories())
                .anySatisfy(advisory -> assertThat(advisory).contains("15°F"));
    }

    @Test
    @DisplayName("the sun warning appears on mild days too, not only hot ones")
    void sunWarningAppearsOnMildDays() {
        // A caveat people only see occasionally is a caveat they learn to discount.
        WorkRestSchedule schedule = builder.build(crew("CA", adaptedWorker()), DAY, hours(70));

        assertThat(schedule.advisories())
                .anySatisfy(advisory -> assertThat(advisory).contains("full sunshine"));
    }

    @Test
    @DisplayName("an empty forecast says so rather than implying a safe day")
    void emptyForecastIsNotAnAllClear() {
        // The same principle as RefillRadar's 503: an outage must never be mistakable for
        // good news. An empty break list looks identical to "no heat risk" otherwise.
        WorkRestSchedule schedule = builder.build(crew("CA", adaptedWorker()), DAY, List.of());

        assertThat(schedule.requiresBreaks()).isFalse();
        assertThat(schedule.advisories())
                .anySatisfy(advisory -> assertThat(advisory).contains("Do NOT read that as"));
    }

    @Test
    @DisplayName("a null forecast is tolerated like an empty one")
    void nullForecastIsTolerated() {
        WorkRestSchedule schedule = builder.build(crew("CA", adaptedWorker()), DAY, null);

        assertThat(schedule.hourlyConditions()).isEmpty();
        assertThat(schedule.advisories()).isNotEmpty();
    }

    @Test
    @DisplayName("total rest is the sum of the day's breaks")
    void totalRestSumsBreaks() {
        WorkRestSchedule schedule =
                builder.build(crew("CA", adaptedWorker()), DAY, hours(100, 100, 100));

        long expected = schedule.breaks().stream()
                .mapToLong(ScheduledBreak::durationMinutes).sum();
        assertThat(schedule.totalRestMinutes()).isEqualTo(expected).isPositive();
    }

    @Test
    @DisplayName("a schedule cannot be mutated after it is handed out")
    void scheduleIsImmutable() {
        WorkRestSchedule schedule =
                builder.build(crew("CA", adaptedWorker()), DAY, hours(100));

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> schedule.breaks().add(
                        new ScheduledBreak(LocalDateTime.now(), LocalDateTime.now(), 0, "x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
