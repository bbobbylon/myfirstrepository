package com.shadeclock.rules;

/**
 * How confident ShadeClock is that a ruleset matches the regulation it claims to encode.
 *
 * <h2>Why this is a first-class field and not a code comment</h2>
 * ShadeClock encodes legal thresholds that a supervisor may act on. Getting one wrong is not
 * a cosmetic bug - it could mean a crew works through a break the law required. The honest
 * engineering response to "I believe this threshold is 95°F but I have not read the
 * regulation myself" is not to hide the doubt in a comment nobody reads, but to carry it
 * through to the API response and the user interface.
 *
 * <p>Every ruleset therefore declares its provenance, and any ruleset that is not
 * {@link #VERIFIED_AGAINST_REGULATION} causes a visible caveat in output. That way the
 * product degrades into "useful planning aid with a warning" rather than pretending to be
 * "compliance guarantee".
 */
public enum VerificationStatus {

    /**
     * Someone read the actual regulation text and confirmed every threshold in this
     * ruleset against it, recording the citation.
     */
    VERIFIED_AGAINST_REGULATION(
            "Thresholds confirmed against the published regulation."),

    /**
     * Thresholds came from secondary summaries - news coverage, law-firm briefings,
     * compliance blogs - and have <b>not</b> been checked against the regulation itself.
     *
     * <p>This is the state every ruleset shipped in v0.1 is in, and the README says so.
     */
    UNVERIFIED_SECONDARY_SOURCE(
            "UNVERIFIED: thresholds come from secondary summaries, not the regulation text. "
                    + "Confirm against the citation before relying on this for compliance."),

    /**
     * There is no enforceable standard for this jurisdiction, so the ruleset encodes
     * general guidance rather than law.
     */
    GUIDANCE_NOT_LAW(
            "No enforceable standard in this jurisdiction - these are general precautions, "
                    + "not legal requirements.");

    private final String caveat;

    VerificationStatus(String caveat) {
        this.caveat = caveat;
    }

    /**
     * The caveat to show alongside any schedule built with this ruleset.
     *
     * @return the caveat text, never {@code null}
     */
    public String caveat() {
        return caveat;
    }

    /**
     * Whether output built on this ruleset must carry a prominent warning.
     *
     * @return {@code true} unless the ruleset was verified against the regulation
     */
    public boolean requiresWarning() {
        return this != VERIFIED_AGAINST_REGULATION;
    }
}
