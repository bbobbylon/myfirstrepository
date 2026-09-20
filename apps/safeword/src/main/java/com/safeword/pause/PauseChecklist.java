package com.safeword.pause;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * The full-screen, unskippable checklist shown when someone presses the pause button.
 *
 * <h2>Urgency is the attack; friction is the countermeasure</h2>
 * Every one of these scams runs on manufactured time pressure - bail money now, the warrant
 * is being issued, don't tell anyone. The pressure is not incidental to the scam; it <em>is</em>
 * the scam, because it prevents the one thing that reliably defeats it: stopping to check.
 *
 * <p>So SafeWord's core interaction is deliberately slow. One button produces a screen that
 * cannot be dismissed in a hurry, stating a handful of facts that are true regardless of what
 * the caller is saying.
 *
 * <h2>Design constraints that outrank anything technical</h2>
 * The reader may be 78, may have reduced vision, and <b>will be frightened</b>. That is not an
 * edge case, it is the design centre:
 * <ul>
 *   <li>Minimum 18pt type, very high contrast</li>
 *   <li>One idea per line; no paragraphs</li>
 *   <li>No jargon, no onboarding carousel, no dark patterns</li>
 *   <li>Nothing that requires fine motor control or fast reading</li>
 * </ul>
 * If only one thing in this app is built well, it should be this screen. An accessible
 * interface is not a nice-to-have here - it <em>is</em> the product.
 */
@Service
public class PauseChecklist {

    /**
     * The facts shown on the pause screen, in display order.
     *
     * <p>Each is short, absolute, and true independent of what the caller claims. They are
     * deliberately phrased as statements about the world rather than instructions about the
     * caller, so they hold even if the person half-believes the story.
     *
     * @return the checklist items, never {@code null}
     */
    public List<ChecklistItem> items() {
        return List.of(
                new ChecklistItem(
                        "No real organisation asks for gift cards.",
                        "Not the police. Not the IRS. Not your bank. Not Amazon. Never.",
                        true),
                new ChecklistItem(
                        "No real emergency gets worse if you take ten minutes.",
                        "Anyone who says otherwise is telling you that to stop you checking.",
                        true),
                new ChecklistItem(
                        "Hang up and call back on a number you already have.",
                        "Not a number they gave you. Not a number from caller ID. One from "
                                + "your own contacts, a statement, or an official website.",
                        true),
                new ChecklistItem(
                        "A familiar voice no longer proves who it is.",
                        "Voices can be copied from a few seconds of audio. Ask for your "
                                + "family passphrase instead.",
                        true),
                new ChecklistItem(
                        "'Don't tell anyone' is the biggest warning sign there is.",
                        "Real situations do not require you to hide them from your family.",
                        true),
                new ChecklistItem(
                        "You can always call someone you trust first.",
                        "There is no penalty for checking. Press the button below and we will "
                                + "let your circle know you want a call.",
                        false));
    }

    /**
     * The single most important line, for the top of the screen.
     *
     * @return the headline, never {@code null}
     */
    public String headline() {
        return "Take your time. Nothing bad happens because you paused.";
    }

    /**
     * One item on the pause screen.
     *
     * @param statement  the short fact, shown large
     * @param detail     supporting line, shown smaller
     * @param isAbsolute whether this holds regardless of the caller's story
     */
    public record ChecklistItem(String statement, String detail, boolean isAbsolute) {
    }
}
