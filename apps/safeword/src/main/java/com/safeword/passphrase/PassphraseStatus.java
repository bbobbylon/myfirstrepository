package com.safeword.passphrase;

/**
 * Whether a family has a usable passphrase - tracked without ever knowing what it is.
 */
public enum PassphraseStatus {

    /**
     * No passphrase agreed. The protection is not active.
     *
     * <p>Nagged about, because someone who installs SafeWord and never agrees a passphrase
     * is <b>worse off than before</b> - they now believe they are protected.
     */
    NOT_AGREED("No passphrase yet - SafeWord is not protecting you until you agree one"),

    /** Agreed in person and current. */
    AGREED("Passphrase agreed - ask for it on any call about money or secrecy"),

    /**
     * Agreed, but long enough ago that a reminder is due.
     *
     * <p>Not a failure - a prompt. A passphrase only works if it is remembered under stress,
     * and memory of something never used decays.
     */
    NEEDS_REFRESH("Agreed a while ago - worth a quick practice so everyone still remembers it");

    private final String explanation;

    PassphraseStatus(String explanation) {
        this.explanation = explanation;
    }

    /**
     * A plain-language explanation for display.
     *
     * @return the explanation, never {@code null}
     */
    public String explanation() {
        return explanation;
    }

    /**
     * Whether SafeWord's core protection is actually active.
     *
     * @return {@code true} unless no passphrase has been agreed
     */
    public boolean isProtectionActive() {
        return this != NOT_AGREED;
    }
}
