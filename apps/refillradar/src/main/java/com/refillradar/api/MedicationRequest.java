package com.refillradar.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Incoming payload for registering a medication.
 *
 * <p>Separate from {@link com.refillradar.domain.Medication} on purpose. The domain record
 * generates its own id and enforces business invariants; this type describes what a client
 * is allowed to send. Binding HTTP requests straight onto domain objects is how clients end
 * up able to set fields you never meant to expose.
 *
 * <p>The {@code jakarta.validation} annotations are enforced by Spring before the controller
 * method runs, so a bad request is rejected with a {@code 400} at the edge rather than
 * throwing somewhere deep in the engine.
 *
 * <p><b>There is no {@code userId} field, and that is the security fix.</b> Through v0.3
 * there was one, and it was trusted, so a client could file a medication under any account
 * it named. The owner now comes from the session instead. A field a client cannot send is a
 * field nobody has to remember to validate.
 *
 * @param displayName  what the user calls it, e.g. {@code "Adderall XR 10mg"}
 * @param searchTerm   optional generic name to match on; defaults to {@code displayName}
 * @param lastFilledOn the date it was last dispensed
 * @param daysSupply   how many days that fill covers
 */
public record MedicationRequest(
        @NotBlank(message = "displayName is required")
        String displayName,

        String searchTerm,

        @NotNull(message = "lastFilledOn is required (format: YYYY-MM-DD)")
        LocalDate lastFilledOn,

        @Positive(message = "daysSupply must be a positive number of days")
        int daysSupply) {
}
