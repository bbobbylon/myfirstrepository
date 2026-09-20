package com.safeword.scam;

/**
 * A tactic reported during a suspicious call.
 *
 * <h2>Why SafeWord scores tactics, not voices</h2>
 * Every one of these is something the <em>caller does</em>, observable by the person on the
 * phone without any technical judgement. That is the opposite of asking "does this voice
 * sound real?", which is a question humans can no longer answer and software should not
 * pretend to.
 *
 * <p>The tactics are stable across scam generations because they exploit human psychology
 * rather than technology. Voice cloning changed how convincing the <em>voice</em> is; it did
 * not change the need to manufacture urgency, isolate the target, and demand an irreversible
 * payment. Those three remain the signature, and they are what SafeWord looks at.
 */
public enum PressureSignal {

    /** "Right now", "before the end of the day", "there's no time". */
    URGENCY("They are pushing you to act immediately", 3),

    /** "Don't tell your family", "this is confidential". */
    SECRECY("They asked you not to tell anyone", 4),

    /** Gift cards, wire transfer, crypto, payment apps, couriered cash. */
    IRREVERSIBLE_PAYMENT("They want gift cards, a wire, crypto or cash", 5),

    /** Police, IRS, Social Security, Medicare, your bank's "fraud department". */
    AUTHORITY_CLAIM("They claim to be a government agency, police or your bank", 3),

    /** A relative in trouble - the classic grandparent scam, now with a cloned voice. */
    FAMILY_EMERGENCY("They say a family member is in trouble and needs money", 4),

    /** They called you, rather than you calling a number you already had. */
    INBOUND_CONTACT("They contacted you - you did not call them", 2),

    /** Remote access to a computer, or reading out codes from your screen or phone. */
    REMOTE_ACCESS("They want access to your computer, or codes from your screen", 5),

    /** Guaranteed returns, a limited opportunity, a platform you had not heard of. */
    INVESTMENT_PITCH("They are offering an investment with unusually good returns", 4);

    private final String description;
    private final int weight;

    PressureSignal(String description, int weight) {
        this.description = description;
        this.weight = weight;
    }

    /**
     * A plain-language description, phrased as something the caller did.
     *
     * @return the description, never {@code null}
     */
    public String description() {
        return description;
    }

    /**
     * How strongly this signal indicates a scam.
     *
     * <p>Weights are a product judgement, not a validated model, and are stated in one place
     * so they can be reviewed rather than scattered through scoring code. The heaviest are
     * the two that make a loss unrecoverable: an irreversible payment method, and remote
     * access.
     *
     * @return the weight
     */
    public int weight() {
        return weight;
    }
}
