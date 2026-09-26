package com.shadeclock.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.shadeclock.crew.Crew;
import com.shadeclock.crew.Worker;
import com.shadeclock.heat.HourlyConditions;
import com.shadeclock.rules.HeatRule;
import com.shadeclock.rules.HeatRuleset;
import com.shadeclock.rules.RulesetRegistry;

/**
 * Turns a forecast plus a ruleset into a crew's work/rest timetable.
 *
 * <p>The engine of ShadeClock, and - like RefillRadar's {@code ShortageMatcher} - pure:
 * everything it needs arrives as an argument, so it can be tested exhaustively in
 * milliseconds with no network, no database and no clock of its own.
 */
@Service
public class ScheduleBuilder {

    /** Rest is scheduled at the end of each affected hour. */
    private static final int MINUTES_PER_HOUR = 60;

    private final RulesetRegistry registry;

    /**
     * @param registry supplies the ruleset for a crew's jurisdiction
     */
    public ScheduleBuilder(RulesetRegistry registry) {
        this.registry = registry;
    }

    /**
     * Builds the day's plan.
     *
     * @param crew       the crew to schedule; must not be {@code null}
     * @param date       the day being planned
     * @param conditions hourly forecast for the site, in any order
     * @return the completed schedule, never {@code null}
     */
    public WorkRestSchedule build(Crew crew, LocalDate date, List<HourlyConditions> conditions) {
        HeatRuleset ruleset = registry.forJurisdiction(crew.jurisdiction());
        boolean usedFallback = !registry.hasSpecificRuleset(crew.jurisdiction());

        List<HourlyConditions> ordered = conditions == null ? List.of()
                : conditions.stream().sorted(Comparator.comparing(HourlyConditions::hour)).toList();

        List<ScheduledBreak> breaks = new ArrayList<>();
        for (HourlyConditions hour : ordered) {
            ruleset.governingRestRule(hour.heatIndexF())
                    .ifPresent(rule -> breaks.add(toBreak(hour, rule)));
        }

        HourlyConditions peak = ordered.stream()
                .max(Comparator.comparingDouble(HourlyConditions::heatIndexF))
                .orElse(null);

        List<Worker> needWatching = crew.workersNeedingExtraPrecautions(date);

        return new WorkRestSchedule(
                date,
                crew.name(),
                crew.siteLabel(),
                ruleset.jurisdictionName(),
                ruleset.citation(),
                ruleset.verificationStatus().caveat(),
                usedFallback,
                peak == null ? 0 : peak.displayHeatIndexF(),
                peak == null ? null : peak.hour(),
                ordered,
                breaks,
                needWatching,
                buildAdvisories(crew, date, ruleset, ordered, needWatching, usedFallback));
    }

    /**
     * Converts one hour's governing rule into a concrete break.
     *
     * <p>Rest is placed at the <em>end</em> of the hour - work 11:00-11:50, rest 11:50-12:00.
     * Chosen because it is trivial to explain to a crew and lines up with how breaks are
     * called in practice, rather than because any regulation mandates that placement.
     *
     * @param hour the hour being scheduled
     * @param rule the governing rest rule
     * @return the scheduled break
     */
    private ScheduledBreak toBreak(HourlyConditions hour, HeatRule rule) {
        LocalDateTime start = hour.hour().plusMinutes(MINUTES_PER_HOUR - rule.restMinutesPerHour());
        LocalDateTime end = hour.hour().plusMinutes(MINUTES_PER_HOUR);

        String reason = "Heat index around " + hour.displayHeatIndexF() + "°F ("
                + hour.riskBand().description().toLowerCase() + "). "
                + rule.restMinutesPerHour() + " minutes rest in shade.";

        return new ScheduledBreak(start, end, hour.displayHeatIndexF(), reason, rule.citation());
    }

    /**
     * Assembles the plain-language notes shown above the timetable.
     *
     * <p>Ordered most-important-first, because a supervisor reads the top of a screen on a
     * phone in bright sun and may read nothing else.
     *
     * @param crew          the crew
     * @param date          the day
     * @param ruleset       the ruleset applied
     * @param ordered       the day's conditions in time order
     * @param needWatching  workers with incomplete adaptation
     * @param usedFallback  whether general guidance replaced state rules
     * @return the advisories, never {@code null}
     */
    private List<String> buildAdvisories(Crew crew,
                                         LocalDate date,
                                         HeatRuleset ruleset,
                                         List<HourlyConditions> ordered,
                                         List<Worker> needWatching,
                                         boolean usedFallback) {
        List<String> advisories = new ArrayList<>();

        if (ordered.isEmpty()) {
            advisories.add("No forecast available for this site - this plan is empty. "
                    + "Do NOT read that as 'no heat risk'.");
            return advisories;
        }

        if (!needWatching.isEmpty()) {
            // Named individuals, not a count. "Three workers are at risk" is a statistic;
            // "watch Maria, Dev and Sam" is an instruction.
            String names = needWatching.stream().map(Worker::name).reduce((a, b) -> a + ", " + b)
                    .orElse("");
            advisories.add("Watch these workers closely - their heat adaptation is "
                    + "incomplete: " + names + ".");
        }

        if (usedFallback) {
            advisories.add("No state-specific ruleset for '" + crew.jurisdiction()
                    + "'. Showing general guidance, which is NOT that state's law.");
        }

        if (ruleset.verificationStatus().requiresWarning()) {
            advisories.add(ruleset.verificationStatus().caveat());
        }

        // Stated on every plan, not only hot ones. The limitation does not switch off on
        // mild days, and a caveat people only see occasionally is a caveat they discount.
        advisories.add("Heat index assumes SHADE and light wind. NWS states that full "
                + "sunshine can raise the effective heat index by up to "
                + (int) com.shadeclock.heat.HeatIndexCalculator.FULL_SUN_ADDITION_F
                + "°F, and it does not model radiant heat from machinery or asphalt at all. "
                + "A crew working in open sun may be far past the band shown here. Treat "
                + "this as a screening tool, not a measurement.");

        return advisories;
    }
}
