package com.safeword.scam;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * The library of scam shapes SafeWord recognises, with the reported scale of each.
 *
 * <h2>Why these five, and why the loss figures matter</h2>
 * The patterns below are ordered by what US reporting says they actually cost people aged 60
 * and over, rather than by how memorable they are. In 2025, that age group reported
 * <b>over 201,000 complaints and more than $7.7 billion in losses</b> to the FBI's IC3 -
 * with an average loss above $38,000, and more than 12,400 people losing over $100,000 each.
 * Older adults filed 20% of complaints but bore 37% of the losses.
 *
 * <p>Within that, the reported breakdown was investment fraud <b>$3.52B</b>, tech and customer
 * support <b>$1.04B</b>, and confidence/romance <b>$584M</b>. 2025 was also the first year
 * IC3's annual report broke out a dedicated AI section: <b>22,364 complaints, ~$893M</b>, of
 * which about <b>$352M</b> hit the 60+ group - and that only counts cases where the victim
 * <em>realised</em> AI was involved, so the true figure is necessarily higher. A successful
 * voice clone is one nobody detects.
 *
 * <p>⚠️ Sources disagree on the year-over-year growth rate (some report +59% in losses, others
 * +37%). The base figures above are consistently reported; the growth rate is not, and is not
 * quoted anywhere in this application.
 *
 * <p>The figures appear in user-facing copy deliberately but sparingly. Telling someone
 * "this pattern took $1 billion from people last year" is context that helps them take a pause
 * seriously. Frightening people to sell a subscription would be predatory - especially to an
 * audience that is already a target - so the tone stays factual and the numbers stay sourced.
 */
@Component
public class ScamPatternLibrary {

    private static final List<ScamPattern> PATTERNS = List.of(

            new ScamPattern(
                    "Investment or crypto 'opportunity'",
                    "A contact - sometimes built over weeks, sometimes via a group chat - "
                            + "introduces a trading platform with reliable returns. Small "
                            + "withdrawals work at first. Larger ones require a 'fee' or 'tax', "
                            + "and then the platform goes quiet.",
                    Set.of(PressureSignal.INVESTMENT_PITCH, PressureSignal.URGENCY,
                            PressureSignal.IRREVERSIBLE_PAYMENT, PressureSignal.INBOUND_CONTACT),
                    List.of("Stop sending money. A platform that needs a fee to release your "
                                    + "own funds is not a platform.",
                            "Do not try to recover it through anyone who contacts you offering "
                                    + "to - recovery scams target people who just lost money.",
                            "Report it at ic3.gov, even if you feel embarrassed. You are not the "
                                    + "first and reporting helps."),
                    "The largest reported category for people 60+ in 2025: about $3.52 billion"),

            new ScamPattern(
                    "Tech or customer support",
                    "A pop-up, call or email warns that your computer is infected or an account "
                            + "is compromised. 'Support' asks to connect remotely, then shows you "
                            + "alarming-looking output and asks you to buy gift cards or move "
                            + "money to a 'safe account'.",
                    Set.of(PressureSignal.REMOTE_ACCESS, PressureSignal.AUTHORITY_CLAIM,
                            PressureSignal.URGENCY, PressureSignal.IRREVERSIBLE_PAYMENT),
                    List.of("Hang up. Do not call the number on the warning.",
                            "No real company moves your money to keep it 'safe'. No bank asks "
                                    + "you to buy gift cards. Ever.",
                            "If you gave remote access, disconnect the device from the internet "
                                    + "and get someone you trust to help check it."),
                    "About $1.04 billion reported by people 60+ in 2025"),

            new ScamPattern(
                    "Family emergency - now with a cloned voice",
                    "A call comes from a grandchild or child in trouble: an accident, an arrest, "
                            + "a hospital. They need money now and beg you not to tell their "
                            + "parents. The voice is right, because a few seconds of their social "
                            + "media audio was enough to clone it.",
                    Set.of(PressureSignal.FAMILY_EMERGENCY, PressureSignal.SECRECY,
                            PressureSignal.URGENCY, PressureSignal.IRREVERSIBLE_PAYMENT),
                    List.of("ASK FOR YOUR FAMILY PASSPHRASE. A cloned voice cannot know it.",
                            "Hang up and call that person back on the number you already have. "
                                    + "If they are fine, they will answer.",
                            "The secrecy request is the tell. Real emergencies do not require "
                                    + "you to hide them from family."),
                    "Voice cloning is now layered into these calls; AI-linked losses reported "
                            + "by people 60+ were about $352 million in 2025, and that counts "
                            + "only cases where AI was recognised"),

            new ScamPattern(
                    "Confidence or romance",
                    "A relationship built patiently online - sometimes months - before any "
                            + "request. Then a crisis needs money, or an investment they want to "
                            + "share with you. They always have a reason not to meet.",
                    Set.of(PressureSignal.SECRECY, PressureSignal.IRREVERSIBLE_PAYMENT,
                            PressureSignal.INBOUND_CONTACT, PressureSignal.INVESTMENT_PITCH),
                    List.of("Stop sending money and talk to someone you trust in person.",
                            "Ask for a live video call on your terms. Refusals and endless "
                                    + "excuses are the answer.",
                            "Being deceived by someone skilled at this is not foolish. Telling "
                                    + "someone is the fastest way out."),
                    "About $584 million reported by people 60+ in 2025"),

            new ScamPattern(
                    "Government or bank impersonation",
                    "A caller claims to be from the police, the IRS, Social Security, Medicare "
                            + "or your bank's fraud team. There is a warrant, a suspended number, "
                            + "or a compromised account - and a payment or transfer will fix it.",
                    Set.of(PressureSignal.AUTHORITY_CLAIM, PressureSignal.URGENCY,
                            PressureSignal.IRREVERSIBLE_PAYMENT, PressureSignal.INBOUND_CONTACT),
                    List.of("Hang up. Government agencies do not phone demanding payment, and "
                                    + "never in gift cards.",
                            "Call the organisation back on a number from your own statement, "
                                    + "card or their official website - never one they gave you.",
                            "Caller ID can be faked. A number that looks official proves "
                                    + "nothing."),
                    "Impersonation is among the most commonly reported approaches"));

    /**
     * Every pattern in the library.
     *
     * @return the patterns, never {@code null}
     */
    public List<ScamPattern> all() {
        return PATTERNS;
    }

    /**
     * The patterns that best match a set of observed tactics, best first.
     *
     * <p>Returns matches rather than a single verdict on purpose. Real calls blend patterns,
     * and presenting one confident answer would overstate what this can know from a handful
     * of checkboxes. The person is better served by "this looks like one of these two - here
     * is what both have in common" than by a false precision.
     *
     * @param observed the tactics reported
     * @return matching patterns ordered by match strength, never {@code null}
     */
    public List<ScamPattern> matching(Set<PressureSignal> observed) {
        if (observed == null || observed.isEmpty()) {
            return List.of();
        }
        return PATTERNS.stream()
                .filter(pattern -> pattern.matchCount(observed) >= 2)
                .sorted(Comparator.comparingLong(
                        (ScamPattern pattern) -> pattern.matchCount(observed)).reversed())
                .toList();
    }
}
