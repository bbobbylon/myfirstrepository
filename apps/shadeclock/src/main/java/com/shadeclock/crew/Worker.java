package com.shadeclock.crew;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One member of a work crew, with the dates needed to work out heat adaptation.
 *
 * @param id             stable identifier
 * @param name           the worker's name, as the supervisor entered it
 * @param heatWorkStartedOn the first day of the current run of work in heat; must not be
 *                       {@code null}
 * @param lastAbsenceEndedOn the day a break of a week or more ended, or {@code null} if
 *                       there has not been one during this run
 */
public record Worker(
        String id,
        String name,
        LocalDate heatWorkStartedOn,
        LocalDate lastAbsenceEndedOn) {

    /**
     * Days away that cost a worker their adaptation.
     *
     * <p>Chosen to match the commonly-cited guidance that roughly a week away begins to
     * erode heat adaptation. Named rather than inlined so the assumption is visible and
     * can be changed in one place if a jurisdiction sets a different figure.
     */
    public static final int ABSENCE_DAYS_THAT_RESET_ADAPTATION = 7;

    /** Days of continuous heat work after which a worker is treated as fully adapted. */
    public static final int DAYS_TO_FULL_ACCLIMATIZATION = 14;

    /** Days during which a worker is at the highest risk. */
    public static final int HIGHEST_RISK_DAYS = 3;

    /**
     * Validates the dates the risk calculation depends on.
     *
     * @throws IllegalArgumentException if {@code heatWorkStartedOn} is missing
     */
    public Worker {
        if (heatWorkStartedOn == null) {
            throw new IllegalArgumentException(
                    "heatWorkStartedOn is required to assess acclimatization for " + name);
        }
    }

    /**
     * Works out this worker's heat adaptation as of a given day.
     *
     * <p>A return from an absence restarts the clock: the effective start date becomes the
     * day they came back, not the day they originally joined. This is the behaviour a
     * foreman would otherwise have to remember manually, and the reason a ten-year veteran
     * can legitimately be flagged as high risk on their first day back from two weeks off.
     *
     * @param today the day to assess; must not be {@code null}
     * @return the worker's status on that day, never {@code null}
     */
    public AcclimatizationStatus acclimatizationOn(LocalDate today) {
        LocalDate effectiveStart = heatWorkStartedOn;
        boolean returningFromAbsence = false;

        if (lastAbsenceEndedOn != null && lastAbsenceEndedOn.isAfter(heatWorkStartedOn)) {
            effectiveStart = lastAbsenceEndedOn;
            returningFromAbsence = true;
        }

        long daysInHeat = ChronoUnit.DAYS.between(effectiveStart, today);

        // A start date in the future means bad data, not a superhumanly adapted worker.
        // Fail towards caution rather than towards a reassuring answer.
        if (daysInHeat < 0) {
            return AcclimatizationStatus.UNACCLIMATIZED;
        }
        if (daysInHeat >= DAYS_TO_FULL_ACCLIMATIZATION) {
            return AcclimatizationStatus.ACCLIMATIZED;
        }
        if (returningFromAbsence && daysInHeat < HIGHEST_RISK_DAYS) {
            return AcclimatizationStatus.LOST_ACCLIMATIZATION;
        }
        if (daysInHeat < HIGHEST_RISK_DAYS) {
            return AcclimatizationStatus.UNACCLIMATIZED;
        }
        return AcclimatizationStatus.ACCLIMATIZING;
    }
}
