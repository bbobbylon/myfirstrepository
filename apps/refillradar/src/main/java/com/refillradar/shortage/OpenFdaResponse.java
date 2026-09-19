package com.refillradar.shortage;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Jackson binding for the raw {@code /drug/shortages.json} payload.
 *
 * <p>Kept deliberately separate from {@link com.refillradar.domain.ShortageRecord}. This
 * class is shaped by the FDA; the domain record is shaped by our needs. Translating between
 * them in exactly one place ({@link OpenFdaShortageSource}) means a field rename upstream
 * breaks one file rather than rippling through the matcher and every test.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} is load-bearing: openFDA can add
 * fields at any time, and without it a new field upstream would crash every sync. Being
 * liberal in what you accept is the right posture for a feed you do not control.
 *
 * <p>Field names follow openFDA's published searchable fields for this endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenFdaResponse {

    /** The records for this page of results; {@code null} when the query matched nothing. */
    @JsonProperty("results")
    public List<Result> results;

    /**
     * One shortage entry exactly as the FDA publishes it.
     *
     * <p>Public fields rather than getters: this is a short-lived parsing artefact that
     * never escapes the {@code shortage} package, so ceremony would add noise without
     * adding safety. Everything crossing a package boundary is an immutable record instead.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        /** Active ingredient name, e.g. {@code "AMPHETAMINE ASPARTATE; AMPHETAMINE SULFATE"}. */
        @JsonProperty("generic_name")
        public String genericName;

        /** Brand name where the FDA supplies one. */
        @JsonProperty("proprietary_name")
        public String proprietaryName;

        /** Reporting manufacturer. */
        @JsonProperty("company_name")
        public String companyName;

        /** Free-text lifecycle state; parsed by {@code ShortageStatus.fromFdaStatus}. */
        @JsonProperty("status")
        public String status;

        /** Free-text note on current supply. */
        @JsonProperty("availability")
        public String availability;

        /** The FDA's stated cause, e.g. a manufacturing delay. */
        @JsonProperty("shortage_reason")
        public String shortageReason;

        /** Therapeutic categories assigned by the FDA. */
        @JsonProperty("therapeutic_category")
        public List<String> therapeuticCategory;

        /** Dosage form, e.g. {@code "TABLET"}. */
        @JsonProperty("dosage_form")
        public String dosageForm;

        /** Affected strengths. */
        @JsonProperty("strength")
        public List<String> strength;

        /** When the shortage was first posted. */
        @JsonProperty("initial_posting_date")
        public String initialPostingDate;

        /** When the FDA last updated this record. */
        @JsonProperty("update_date")
        public String updateDate;
    }
}
