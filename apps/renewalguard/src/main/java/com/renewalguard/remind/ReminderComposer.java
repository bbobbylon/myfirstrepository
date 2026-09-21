package com.renewalguard.remind;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.renewalguard.checklist.RequiredDocument;
import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.RenewalUrgency;

/**
 * Writes the words a person actually reads.
 *
 * <p>Three rules, none of them stylistic:
 *
 * <ol>
 *   <li><b>Never say whether someone qualifies.</b> Eligibility is the state agency's legal
 *       determination. A message implying "you probably will not qualify anyway" could talk
 *       someone out of a renewal they would have won - which is the exact harm this app
 *       exists to prevent, delivered by the app itself.</li>
 *   <li><b>Never ask for sensitive data in a message.</b> No "reply with your SSN", ever.
 *       This audience is heavily targeted by benefits scams, and a legitimate service that
 *       behaves like a scam teaches people the wrong reflex.</li>
 *   <li><b>Always point at the notice and the agency.</b> RenewalGuard's date is a best
 *       estimate; the person's own notice is authoritative. Every message says so.</li>
 * </ol>
 *
 * <p>Stigma is a documented barrier to benefit uptake, so the copy stays matter-of-fact -
 * free of both alarm and cheerfulness.
 */
@Service
public class ReminderComposer {

    private static final DateTimeFormatter FRIENDLY_DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    /**
     * Composes the subject line.
     *
     * @param benefitCase  the case
     * @param urgency      how urgent it is
     * @param daysUntilDue days remaining; negative if past
     * @return the subject line, never {@code null}
     */
    public String composeSubject(BenefitCase benefitCase, RenewalUrgency urgency, long daysUntilDue) {
        String program = benefitCase.program().label();

        if (daysUntilDue < 0) {
            return program + " renewal date has passed - you may still be able to act";
        }
        if (urgency == RenewalUrgency.CRITICAL) {
            return program + " renewal due in " + daysUntilDue + " day"
                    + (daysUntilDue == 1 ? "" : "s");
        }
        return program + " renewal coming up on "
                + benefitCase.renewalDueOn().format(FRIENDLY_DATE);
    }

    /**
     * Composes the message body.
     *
     * @param benefitCase     the case
     * @param urgency         how urgent it is
     * @param daysUntilDue    days remaining; negative if past
     * @param documents       the checklist to include
     * @param needsAddressCheck whether to include the address prompt
     * @param windowExplanation the response-window caveat from {@code ResponseWindow}
     * @return the message body as plain text
     */
    public String composeBody(BenefitCase benefitCase,
                              RenewalUrgency urgency,
                              long daysUntilDue,
                              List<RequiredDocument> documents,
                              boolean needsAddressCheck,
                              String windowExplanation) {

        StringBuilder body = new StringBuilder();

        body.append(urgency.headline()).append(".\n\n");

        if (daysUntilDue < 0) {
            // The single most important paragraph in the application. People assume a passed
            // deadline is final and stop trying; often it is not.
            body.append("Your ").append(benefitCase.program().label())
                .append(" renewal was due on ")
                .append(benefitCase.renewalDueOn().format(FRIENDLY_DATE))
                .append(", which has passed.\n\n")
                .append("This does NOT automatically mean you have lost coverage, and it is "
                        + "often still possible to put right. Many people are dropped for "
                        + "paperwork reasons while still qualifying. Call your state agency "
                        + "today and ask what your options are.\n\n");
        } else {
            body.append("Your ").append(benefitCase.program().label())
                .append(" renewal is due on ")
                .append(benefitCase.renewalDueOn().format(FRIENDLY_DATE))
                .append(" - that is ").append(daysUntilDue).append(" day")
                .append(daysUntilDue == 1 ? "" : "s").append(" away.\n\n")
                .append(urgency.guidance()).append("\n\n");
        }

        if (needsAddressCheck) {
            // Undelivered mail is one of the largest causes of procedural termination, and
            // this prompt is close to free to deliver.
            body.append("Have you moved since you last contacted them?\n")
                .append("  If your agency does not have your current address, your renewal "
                        + "notice goes to the wrong place and your case can close without "
                        + "you ever seeing a letter. Updating your address takes a phone "
                        + "call.\n\n");
        }

        if (documents != null && !documents.isEmpty()) {
            body.append("What to have ready\n");
            for (RequiredDocument document : documents) {
                body.append("  - ").append(document.label()).append("\n")
                    .append("      ").append(document.whereToFind()).append("\n");
            }
            body.append("\n");
        }

        body.append("About these dates\n")
            .append("  ").append(windowExplanation).append("\n")
            .append("  Your own renewal notice is the authority on your deadline and on what "
                    + "your agency wants. If our date and your notice disagree, believe the "
                    + "notice, and tell us so we can correct it.\n\n");

        // A standing anti-scam line. This audience is heavily targeted, and a legitimate
        // service should actively teach the reflex rather than rely on it.
        body.append("We will never ask you for your Social Security number, bank details or "
                    + "a payment. Nobody legitimate will ask you to pay to renew benefits.\n");

        return body.toString();
    }
}
