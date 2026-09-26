package com.shadeclock.schedule;

import java.time.LocalDateTime;

/**
 * One rest period in a crew's day, with the reason it was scheduled.
 *
 * <p>{@code reason} and {@code citation} exist so the schedule can defend itself. A foreman
 * asked "why are we stopping at 11:50?" gets an answer naming the heat index and the rule,
 * rather than "the app said so" - and an app that cannot explain itself is one that gets
 * overridden the first time it is inconvenient.
 *
 * @param start        when the break begins
 * @param end          when the break ends
 * @param heatIndexF   the heat index that triggered it, rounded for display
 * @param reason       plain-language explanation
 * @param citation     the rule that required it
 */
public record ScheduledBreak(
        LocalDateTime start,
        LocalDateTime end,
        int heatIndexF,
        String reason,
        String citation) {

    /**
     * How long this break lasts.
     *
     * @return duration in minutes
     */
    public long durationMinutes() {
        return java.time.Duration.between(start, end).toMinutes();
    }
}
