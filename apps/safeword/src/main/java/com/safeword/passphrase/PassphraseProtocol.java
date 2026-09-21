package com.safeword.passphrase;

import org.springframework.stereotype.Component;

/**
 * The rules of the family passphrase, and the guarantee that this server never learns it.
 *
 * <p>A shared secret beats voice-deepfake detection: detection is an arms race where a false
 * negative ("this voice appears genuine") causes the loss, whereas a perfect voice clone
 * carries no shared secret and no better model ever changes that.
 *
 * <p><b>Absolute rule:</b> SafeWord records <em>whether</em> a family agreed a passphrase and
 * <em>when</em> - never the phrase itself, not even hashed. {@link #rejectsSuppliedSecret}
 * enforces that in code so a future convenience feature cannot quietly violate it.
 */
@Component
public class PassphraseProtocol {

    /** How long an agreed passphrase is considered current before we nudge a refresh. */
    public static final int REFRESH_AFTER_DAYS = 365;

    /**
     * The instructions a family follows to agree a passphrase.
     *
     * <p>Insists on in person or on a call you initiated: a passphrase agreed over a channel
     * an attacker already controls is not a secret.
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
     * <p>Every API surface that could carry a secret runs through here, so an endpoint added
     * in haste cannot silently start collecting them.
     *
     * @param suppliedValue a value a client tried to send where a secret might be
     * @return {@code true} if the value is non-empty and must therefore be rejected
     */
    public boolean rejectsSuppliedSecret(String suppliedValue) {
        return suppliedValue != null && !suppliedValue.isBlank();
    }

    /**
     * The message returned when a client tries to send a passphrase. Phrased as an
     * explanation, not an error code - the reader was probably trying to be helpful.
     *
     * @return the explanation, never {@code null}
     */
    public String secretRejectionMessage() {
        return "SafeWord does not accept your passphrase, by design. We never store it, so we "
                + "can never leak it. Agree it in person and keep it only in your memories.";
    }
}
