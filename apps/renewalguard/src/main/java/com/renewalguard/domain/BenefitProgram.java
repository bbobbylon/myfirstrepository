package com.renewalguard.domain;

/**
 * The benefit programme a case belongs to.
 *
 * <p>v0.1 models Medicaid properly and carries SNAP as a recognised-but-unmodelled value, so
 * the API can accept it and say plainly that renewal rules for it are not implemented yet -
 * rather than silently applying Medicaid's cadence to a SNAP case, which would produce a
 * confident and wrong deadline.
 */
public enum BenefitProgram {

    /** Medicaid. Fully modelled in v0.1. */
    MEDICAID("Medicaid", true),

    /** Supplemental Nutrition Assistance Program. Recognised but not yet modelled. */
    SNAP("SNAP (food assistance)", false);

    private final String label;
    private final boolean renewalRulesModelled;

    BenefitProgram(String label, boolean renewalRulesModelled) {
        this.label = label;
        this.renewalRulesModelled = renewalRulesModelled;
    }

    /**
     * A human-readable label.
     *
     * @return the label, never {@code null}
     */
    public String label() {
        return label;
    }

    /**
     * Whether RenewalGuard actually models this programme's renewal cadence.
     *
     * @return {@code true} only where cadence rules exist
     */
    public boolean renewalRulesModelled() {
        return renewalRulesModelled;
    }
}
