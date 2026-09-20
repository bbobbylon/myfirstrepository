package com.safeword.escalate;

import org.springframework.stereotype.Service;

/**
 * Writes the message sent to a family circle when someone asks for help.
 *
 * <h2>Make asking cost one tap and require no explanation</h2>
 * Many victims never tell anyone, and embarrassment is a large part of why. A person
 * mid-call, half-believing a convincing story, is not going to compose a message explaining
 * their situation - so SafeWord composes it for them, in neutral words that carry no
 * admission of having been fooled.
 *
 * <p>The message deliberately does <b>not</b> say "Mum is being scammed". It says she wants a
 * call. If the call turns out to be genuine, nobody has been embarrassed, and the feature
 * stays cheap to use again. A tool that costs dignity to use gets used once.
 */
@Service
public class EscalationComposer {

    /**
     * The message sent to responders.
     *
     * @param protectedPersonName who pressed the button
     * @param aboutMoney          whether the call involved money or a transfer
     * @return the message body, never {@code null}
     */
    public String composeToResponders(String protectedPersonName, boolean aboutMoney) {
        StringBuilder message = new StringBuilder();
        message.append(protectedPersonName).append(" has asked for a call.");

        if (aboutMoney) {
            message.append(" They are on a call about money or a transfer right now.");
        } else {
            message.append(" They are on a call they want a second opinion on.");
        }

        message.append("\n\nPlease ring them now if you can.")
               .append("\n\nWhat helps: stay calm, don't tell them off, and ask them to "
                       + "hang up and call the person or company back on a number they "
                       + "already have. Nothing is lost by checking.");

        return message.toString();
    }

    /**
     * What the person who pressed the button sees afterwards.
     *
     * <p>Reassurance, and a bridge to the safest action - so the screen is useful in the
     * seconds before anyone actually rings back.
     *
     * @param responderCount how many people were notified
     * @return the confirmation, never {@code null}
     */
    public String composeConfirmation(int responderCount) {
        if (responderCount == 0) {
            // Failing honestly. Claiming we told someone when there is nobody to tell would
            // leave a person waiting for a call that is never coming.
            return "You have nobody in your circle yet, so we could not reach anyone. "
                    + "You can still hang up and call someone you trust - that is always "
                    + "safe to do.";
        }
        return "We let " + responderCount + " " + (responderCount == 1 ? "person" : "people")
                + " know you want a call. While you wait: you can hang up. "
                + "A real caller will not mind you ringing them back.";
    }
}
