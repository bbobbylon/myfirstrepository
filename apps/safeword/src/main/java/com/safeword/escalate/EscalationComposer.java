package com.safeword.escalate;

import org.springframework.stereotype.Service;

/**
 * Writes the message sent to a family circle when someone asks for help.
 *
 * <p>Embarrassment is a large part of why victims never tell anyone, so the message carries
 * no admission of having been fooled: it says the person wants a call, never "Mum is being
 * scammed". If the call turns out to be genuine, nobody has lost face and the feature stays
 * cheap to use again. A tool that costs dignity to use gets used once.
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
     * What the person who pressed the button sees afterwards: reassurance plus the safest
     * next action, so the screen is useful in the seconds before anyone rings back.
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
