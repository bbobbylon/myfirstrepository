package com.safeword.passphrase;

import org.springframework.stereotype.Component;

/**
 * The rules of the family passphrase - and, crucially, the guarantee that this server never
 * learns it.
 *
 * <h2>The security insight this whole application is built on</h2>
 * We spent two decades teaching people that a familiar voice proves identity. AI voice
 * cloning made that heuristic <b>actively dangerous</b>: a few seconds of social media audio
 * is enough to reproduce a grandchild's voice convincingly. You cannot patch human intuition,
 * but you can replace the heuristic with a better one.
 *
 * <p><b>The analogy:</b> this is two-factor authentication for human beings. The voice is the
 * password - and passwords are now trivially stealable. The passphrase is the second factor:
 * something <em>known</em>, not something <em>sounded</em>. We accepted years ago that a
 * password alone is not enough to log into email. We simply never applied that lesson to the
 * telephone.
 *
 * <h2>Why a shared secret and NOT deepfake detection</h2>
 * The tempting feature is "AI-powered voice deepfake detection". It is deliberately not built,
 * and the reasoning matters:
 *
 * <ul>
 *   <li>Detection is an arms race defenders are currently losing. Every improvement in
 *       detection is training data for the next generator.</li>
 *   <li>A false negative is catastrophic: the app says "this voice appears genuine", the
 *       person wires $38,000, and <b>the app caused it</b>.</li>
 *   <li>A shared secret is <b>information-theoretically stronger</b> than any detector. A
 *       perfect clone of a voice carries no shared secret whatsoever, and no better model
 *       will ever change that.</li>
 * </ul>
 *
 * <p>Ship the boring thing that actually works.
 *
 * <h2>The absolute rule: the passphrase never reaches this server</h2>
 * SafeWord tracks <em>whether</em> a family has agreed a passphrase and <em>when</em>. It
 * never receives, transmits, stores, logs or hashes the phrase itself. If it never touches
 * our servers, a breach of our servers cannot leak it - and a leaked passphrase would be
 * worse than having none, because the family would still trust it.
 *
 * <p>{@link #rejectsSuppliedSecret(String)} exists so that rule is enforced in code and
 * asserted in tests, rather than being a paragraph in a design document that a future
 * "convenience" feature quietly violates.
 */
@Component
public class PassphraseProtocol {

    /** How long an agreed passphrase is considered current before we nudge a refresh. */
    public static final int REFRESH_AFTER_DAYS = 365;

    /**
     * The instructions a family follows to agree a passphrase.
     *
     * <p>Deliberately insists on <b>in person or on a call you initiated</b>. A passphrase
     * agreed over a channel an attacker already controls is not a secret. And it must never
     * be posted, texted or emailed - the whole point is that it exists only in two people's
     * memories.
     *
     * @return the agreement instructions, never {@code null}
     */
    public String agreementInstructions() {
        return """
               Agree your family passphrase like this:

               1. Do it IN PERSON, or on a call that YOU started to a number you already had.
                  Never agree it over a call that came to you.
               2. Pick something you both remember easily but would never post online.
                  Not a pet's name, not a birthday, not a street you have mentioned publicly.
               3. Do not write it down anywhere a phone or a cloud account can reach.
                  Not in Notes, not in a photo, not in a message.
               4. Agree WHEN to use it: any call asking for money, gift cards, transfers, or
                  secrecy - no matter whose voice it is.
               5. Agree HOW to use it: you ASK the caller for the word. You never say it
                  first, and you never repeat it to anyone who claims to already know it.

               SafeWord never asks you for the word and never stores it. We only record that
               you agreed one, and when.
               """;
    }

    /**
     * How the passphrase is used during a suspicious call.
     *
     * @return the usage rule, never {@code null}
     */
    public String usageRule() {
        return "Ask the caller for your word. Do not say it first. If they cannot say it, "
                + "hang up and call the person back on the number you already have.";
    }

    /**
     * Guard that refuses any attempt to hand the server the actual passphrase.
     *
     * <p>This is the enforcement point for the class's central promise. Any API surface that
     * could conceivably carry a secret runs through here, so a future endpoint added in haste
     * cannot silently start collecting them.
     *
     * @param suppliedValue a value a client tried to send where a secret might be
     * @return {@code true} if the value is non-empty and must therefore be rejected
     */
    public boolean rejectsSuppliedSecret(String suppliedValue) {
        return suppliedValue != null && !suppliedValue.isBlank();
    }

    /**
     * The message returned when a client tries to send a passphrase.
     *
     * <p>Phrased as an explanation rather than an error code, because the person reading it
     * may be a worried adult child who thought they were being helpful.
     *
     * @return the explanation, never {@code null}
     */
    public String secretRejectionMessage() {
        return "SafeWord does not accept your passphrase, by design. We never store it, so we "
                + "can never leak it. Agree it in person and keep it only in your memories.";
    }
}
