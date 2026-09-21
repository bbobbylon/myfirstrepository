package com.safeword.scam;

/**
 * How strongly the reported tactics point to a scam, and what to do.
 *
 * <p>Every level's action routes to the same two safe behaviours - <b>hang up and call back
 * on a number you already have</b>, and <b>ask for the passphrase</b>. Neither costs anything
 * if the call is genuine, which is what makes them safe to recommend even when SafeWord is
 * wrong.
 */
public enum RiskLevel {

    /** Several strong tactics present. Treat as a scam until proven otherwise. */
    STOP("STOP - do not send anything",
            "Hang up now. Call the person or organisation back on a number you already "
                    + "have. Do not use any number they gave you."),

    /** Some concerning tactics. Verify before doing anything irreversible. */
    VERIFY("PAUSE - verify before you act",
            "Ask for your family passphrase. Then hang up and call back on a number you "
                    + "already have."),

    /** Few tactics, but the caller is asking for money. Slow down. */
    SLOW_DOWN("Take your time",
            "There is no emergency that gets worse because you took ten minutes to check."),

    /** Nothing concerning reported. */
    NO_FLAGS("Nothing concerning reported",
            "If anything changes - especially a request for money or secrecy - come back "
                    + "and check again.");

    private final String headline;
    private final String action;

    RiskLevel(String headline, String action) {
        this.headline = headline;
        this.action = action;
    }

    /**
     * The short line to display most prominently.
     *
     * @return the headline, never {@code null}
     */
    public String headline() {
        return headline;
    }

    /**
     * The concrete action to take.
     *
     * @return the action, never {@code null}
     */
    public String action() {
        return action;
    }

    /**
     * Classifies a summed tactic score.
     *
     * <p>⚠️ Thresholds are product judgement, not a validated model. They fire <b>early</b>
     * on purpose: an unnecessary pause costs an awkward phone call, a missed one averages
     * over $38,000 for this age group.
     *
     * @param score the summed weight of observed tactics
     * @return the matching level, never {@code null}
     */
    public static RiskLevel fromScore(int score) {
        if (score >= 9) {
            return STOP;
        }
        if (score >= 5) {
            return VERIFY;
        }
        if (score >= 1) {
            return SLOW_DOWN;
        }
        return NO_FLAGS;
    }
}
