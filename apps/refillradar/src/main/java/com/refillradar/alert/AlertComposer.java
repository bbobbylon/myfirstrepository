package com.refillradar.alert;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.refillradar.domain.ShortageMatch;

/**
 * Turns matches into the words a patient actually reads.
 *
 * <h2>The most important class for staying on the right side of a legal line</h2>
 * RefillRadar is an <em>information</em> tool, not a medical device and not a source of
 * medical advice. That distinction is not decided by a disclaimer at the bottom of an
 * email - it is decided by what the sentences in that email actually say.
 *
 * <p>Three rules govern every string produced here, and they are not stylistic preferences:
 *
 * <ol>
 *   <li><b>Never name an alternative drug.</b> Suggesting a substitute is a clinical
 *       decision. Crossing that line changes what this software legally is.</li>
 *   <li><b>Never assert certainty we do not have.</b> The wording is "may be affected", not
 *       "your medication is unavailable". We are reporting a federal database entry, not
 *       the stock on a specific pharmacy's shelf.</li>
 *   <li><b>Always route to a human and always cite the source.</b> Every message ends at a
 *       pharmacist or prescriber and states where the data came from and when.</li>
 * </ol>
 *
 * <p>The product goal is to hand the patient a <em>well-framed question</em>, not an answer.
 */
@Service
public class AlertComposer {

    private static final DateTimeFormatter FRIENDLY_DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    /**
     * Composes the subject line for a user's alert.
     *
     * @param matches alertable matches, most urgent first; must not be empty
     * @return a subject line that conveys urgency without inducing panic
     * @throws IllegalArgumentException if {@code matches} is null or empty
     */
    public String composeSubject(List<ShortageMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("Cannot compose an alert with no matches");
        }
        ShortageMatch mostUrgent = matches.getFirst();
        String drug = mostUrgent.medication().displayName();

        if (matches.size() == 1) {
            return "Possible supply issue affecting your " + drug;
        }
        return "Possible supply issues affecting " + matches.size() + " of your medications";
    }

    /**
     * Composes the alert body.
     *
     * @param matches      alertable matches, most urgent first; must not be empty
     * @param sourceLabel  provenance string from {@code ShortageSource.describeSource()}
     * @return the full message body as plain text
     * @throws IllegalArgumentException if {@code matches} is null or empty
     */
    public String composeBody(List<ShortageMatch> matches, String sourceLabel) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("Cannot compose an alert with no matches");
        }

        StringBuilder body = new StringBuilder();
        body.append("We check the US FDA drug shortage database against the medications ")
            .append("you have told us about. Here is what we found.\n\n");

        for (ShortageMatch match : matches) {
            body.append(describeOne(match)).append("\n");
        }

        body.append("\nWhat this does and does not mean\n")
            .append("  - The FDA lists a national supply issue for this medicine.\n")
            .append("  - It does NOT mean your pharmacy is out of stock. Many pharmacies\n")
            .append("    are unaffected, and your pharmacist can check.\n")
            .append("  - We cannot and do not suggest alternative medicines. That is a\n")
            .append("    decision for your prescriber or pharmacist.\n\n");

        body.append("A question you can ask\n")
            .append("  \"The FDA lists a shortage affecting ")
            .append(matches.getFirst().shortage().displayName())
            .append(". My current supply runs out around ")
            .append(matches.getFirst().runOutDate().format(FRIENDLY_DATE))
            .append(". Can we check availability, or plan ahead?\"\n\n");

        body.append("Source: ").append(sourceLabel).append("\n");
        return body.toString();
    }

    /**
     * Renders a single match as a short block of text.
     *
     * @param match the match to describe
     * @return the description, ending in a newline-free string
     */
    private String describeOne(ShortageMatch match) {
        StringBuilder line = new StringBuilder();

        line.append("* ").append(match.medication().displayName()).append("\n");
        line.append("    Your supply runs out around ")
            .append(match.runOutDate().format(FRIENDLY_DATE));

        if (match.daysRemaining() >= 0) {
            line.append(" (about ").append(match.daysRemaining()).append(" days away)");
        } else {
            // Stale data is far more likely than a patient genuinely out of medicine, so
            // the copy invites a correction rather than raising an alarm.
            line.append(" (already passed - have you refilled since?)");
        }
        line.append(".\n");

        line.append("    FDA record: ").append(match.shortage().displayName());
        if (match.shortage().shortageReason() != null
                && !match.shortage().shortageReason().isBlank()) {
            line.append(" - ").append(match.shortage().shortageReason());
        }
        line.append("\n");

        line.append("    Suggested next step: ")
            .append(match.risk().recommendedAction()).append("\n");

        if (match.hasUncertainStatus()) {
            // Uncertainty is shown, never hidden. A user who knows our confidence is low
            // can weigh the alert appropriately; one who does not, cannot.
            line.append("    Note: we could not fully interpret the FDA's status for this\n")
                .append("    record, so we are flagging it to be safe. Please verify.\n");
        }
        return line.toString();
    }
}
