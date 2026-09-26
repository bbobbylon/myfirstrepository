package com.refillradar.domain;

import java.time.LocalDate;

/**
 * A medication a user has told us they take, together with the refill facts we need to work
 * out when they run out.
 *
 * <p><b>This is the whole product thesis in one class.</b> The FDA already publishes its
 * shortage list, and several tools already display it. What nobody else combines with that
 * list is the field below called {@code lastFilledOn}. Knowing a drug is in shortage is
 * background noise; knowing <em>your</em> drug is in shortage and your last tablet is eleven
 * days away is an appointment you book today. The unique input is the refill date.
 *
 * @param id             stable identifier for this entry
 * @param userId         the owner; scopes every query so one user can never see another's list
 * @param displayName    exactly what the user typed, e.g. {@code "Adderall XR 10mg"} - kept
 *                       verbatim so alerts can echo their own words back to them
 * @param searchTerm     the name used for matching, normally the generic/active ingredient
 * @param lastFilledOn   the date the prescription was last dispensed
 * @param daysSupply     how many days that fill was intended to cover; must be positive
 */
public record Medication(
        String id,
        String userId,
        String displayName,
        String searchTerm,
        LocalDate lastFilledOn,
        int daysSupply) {

    /**
     * Compact constructor validating the fields the projection maths depends on.
     *
     * <p>We fail fast here rather than tolerate bad input, because the failure mode of a
     * zero or negative {@code daysSupply} is a nonsensical run-out date presented to a
     * patient as fact. An exception at entry is cheap; a wrong date in an alert is not.
     *
     * @throws IllegalArgumentException if {@code daysSupply} is not positive, or if
     *                                  {@code lastFilledOn} is {@code null}
     */
    public Medication {
        if (daysSupply <= 0) {
            throw new IllegalArgumentException(
                    "daysSupply must be positive, got " + daysSupply + " for " + displayName);
        }
        if (lastFilledOn == null) {
            throw new IllegalArgumentException("lastFilledOn is required for " + displayName);
        }
        // Fall back to the display name so a medication is never unmatchable just because
        // the caller omitted an explicit search term.
        if (searchTerm == null || searchTerm.isBlank()) {
            searchTerm = displayName;
        }
    }

    /**
     * The date this supply is projected to run out.
     *
     * <p>Deliberately simple: last fill date plus days of supply. It assumes perfect
     * adherence and no early refill, which is not always true in reality - but the
     * assumption errs towards a <em>later</em> run-out date than a patient who misses doses
     * would actually experience, and the whole point of the app is to warn early. See
     * {@code RefillProjector} for how that projection is turned into an urgency level.
     *
     * @return the projected date the last dose is taken
     */
    public LocalDate projectedRunOutDate() {
        return lastFilledOn.plusDays(daysSupply);
    }
}
