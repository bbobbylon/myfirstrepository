package com.shadeclock.schedule;

import java.time.LocalDate;
import java.util.List;

import com.shadeclock.crew.Worker;
import com.shadeclock.heat.HourlyConditions;

/**
 * A crew's heat plan for one day: the timetable, the warnings, and the caveats.
 *
 * <p>A timetable, not a risk colour. "High risk today" tells a supervisor what they can
 * already feel; "10 minutes rest in shade at the end of each hour from 11:00" is a decision
 * they can carry out. Converting weather into a schedule is the entire product.
 *
 * @param date                   the day this plan covers
 * @param crewName               the crew it was built for
 * @param siteLabel              where they are working
 * @param jurisdictionName       whose rules were applied
 * @param citation               the source of those rules
 * @param verificationCaveat     the ruleset's honesty warning; never {@code null}
 * @param usedFallbackRuleset    whether general guidance was substituted for state rules
 * @param peakHeatIndexF         the day's highest heat index, rounded
 * @param peakHour               the hour at which the peak occurs, or {@code null} if no hours
 * @param hourlyConditions       the forecast this plan was built from
 * @param breaks                 the scheduled rest periods, in time order
 * @param workersNeedingWatching crew members whose heat adaptation is incomplete
 * @param advisories             extra plain-language notes for the supervisor
 */
public record WorkRestSchedule(
        LocalDate date,
        String crewName,
        String siteLabel,
        String jurisdictionName,
        String citation,
        String verificationCaveat,
        boolean usedFallbackRuleset,
        int peakHeatIndexF,
        java.time.LocalDateTime peakHour,
        List<HourlyConditions> hourlyConditions,
        List<ScheduledBreak> breaks,
        List<Worker> workersNeedingWatching,
        List<String> advisories) {

    /**
     * Defensive-copies every list so a schedule cannot be mutated after it is handed out.
     */
    public WorkRestSchedule {
        hourlyConditions = hourlyConditions == null ? List.of() : List.copyOf(hourlyConditions);
        breaks = breaks == null ? List.of() : List.copyOf(breaks);
        workersNeedingWatching =
                workersNeedingWatching == null ? List.of() : List.copyOf(workersNeedingWatching);
        advisories = advisories == null ? List.of() : List.copyOf(advisories);
    }

    /**
     * Total scheduled rest across the day.
     *
     * @return minutes of rest
     */
    public long totalRestMinutes() {
        return breaks.stream().mapToLong(ScheduledBreak::durationMinutes).sum();
    }

    /**
     * Whether the day requires any heat-driven rest at all.
     *
     * @return {@code true} if at least one break was scheduled
     */
    public boolean requiresBreaks() {
        return !breaks.isEmpty();
    }
}
