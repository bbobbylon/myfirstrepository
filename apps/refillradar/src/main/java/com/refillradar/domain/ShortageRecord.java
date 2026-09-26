package com.refillradar.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * One drug-shortage entry as published by the FDA, normalised into this application's terms.
 *
 * <p>An anti-corruption layer between the FDA's JSON shape and ours, so a field rename
 * upstream breaks one class rather than leaking into the matcher and every test. Field
 * names mirror openFDA's published searchable fields.
 *
 * <p>A {@code record} because data pulled from an external feed should be immutable.
 *
 * @param genericName         active-ingredient name, e.g. {@code "amphetamine aspartate"}
 * @param proprietaryName     brand name if the FDA supplied one, e.g. {@code "Adderall"};
 *                            may be {@code null}
 * @param companyName         the reporting manufacturer; may be {@code null}
 * @param status              parsed lifecycle state; never {@code null}
 * @param availability        free-text supply note from the FDA; may be {@code null}
 * @param shortageReason      the FDA's stated cause, e.g. a manufacturing delay; may be
 *                            {@code null}
 * @param therapeuticCategory categories the FDA assigned; never {@code null}, possibly empty
 * @param dosageForm          e.g. {@code "TABLET"}; may be {@code null}
 * @param strengths           strengths affected; never {@code null}, possibly empty
 * @param initialPostingDate  when the shortage was first posted; may be {@code null}
 * @param updateDate          when the FDA last updated this record; may be {@code null}
 */
public record ShortageRecord(
        String genericName,
        String proprietaryName,
        String companyName,
        ShortageStatus status,
        String availability,
        String shortageReason,
        List<String> therapeuticCategory,
        String dosageForm,
        List<String> strengths,
        LocalDate initialPostingDate,
        LocalDate updateDate) {

    /**
     * Compact constructor enforcing the invariants the rest of the codebase relies on.
     *
     * <p>Null-safety is applied here, once, at the boundary. Every consumer downstream may
     * then assume {@code status} is non-null and the list fields are safe to iterate. The
     * alternative - defensive null checks scattered across the matcher and the alert
     * composer - is how codebases rot.
     */
    public ShortageRecord {
        if (status == null) {
            status = ShortageStatus.UNKNOWN;
        }
        therapeuticCategory = therapeuticCategory == null ? List.of() : List.copyOf(therapeuticCategory);
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
    }

    /**
     * Whether a patient holding this drug should be warned.
     *
     * @return {@code true} unless the FDA has affirmatively marked the shortage resolved
     */
    public boolean isPotentiallyActive() {
        return status.isPotentiallyActive();
    }

    /**
     * A human-readable label for this record, preferring the brand name patients recognise.
     *
     * <p>Patients say "my Adderall", not "my amphetamine aspartate; amphetamine sulfate".
     * Matching runs on the generic name, but display should use the word the person uses.
     *
     * @return the proprietary name when present, otherwise the generic name, otherwise
     *         {@code "Unknown product"}
     */
    public String displayName() {
        if (proprietaryName != null && !proprietaryName.isBlank()) {
            return proprietaryName;
        }
        if (genericName != null && !genericName.isBlank()) {
            return genericName;
        }
        return "Unknown product";
    }
}
