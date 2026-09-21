package com.safeword.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.safeword.passphrase.PassphraseProtocol;
import com.safeword.passphrase.PassphraseStatus;

/**
 * A family group, its members, and whether it has agreed a passphrase.
 *
 * <p><b>The passphrase itself is not here, and never will be.</b> This record holds only the
 * date it was agreed. See {@link PassphraseProtocol} for why that is an absolute rule rather
 * than a default.
 *
 * @param id                  stable identifier
 * @param name                what the family calls this circle
 * @param passphraseAgreedOn  when the passphrase was agreed in person, or {@code null}
 * @param members             the people in it
 */
public record FamilyCircle(
        String id,
        String name,
        LocalDate passphraseAgreedOn,
        List<CircleMember> members) {

    /**
     * Defensive-copies the member list.
     */
    public FamilyCircle {
        members = members == null ? List.of() : List.copyOf(members);
    }

    /**
     * The circle's passphrase state as of a given day.
     *
     * @param today the day to assess
     * @return the status, never {@code null}
     */
    public PassphraseStatus passphraseStatus(LocalDate today) {
        if (passphraseAgreedOn == null) {
            return PassphraseStatus.NOT_AGREED;
        }
        long daysSince = ChronoUnit.DAYS.between(passphraseAgreedOn, today);
        if (daysSince >= PassphraseProtocol.REFRESH_AFTER_DAYS) {
            return PassphraseStatus.NEEDS_REFRESH;
        }
        return PassphraseStatus.AGREED;
    }

    /**
     * Whether setup is genuinely finished.
     *
     * <p>A circle with members but no agreed passphrase is <b>not</b> set up, however
     * complete it looks - the core mechanism does not exist yet.
     *
     * @param today the day to assess
     * @return {@code true} only when a passphrase is agreed and at least one responder exists
     */
    public boolean isSetupComplete(LocalDate today) {
        return passphraseStatus(today).isProtectionActive() && !responders().isEmpty();
    }

    /**
     * The members who get called when help is requested.
     *
     * @return the responders, never {@code null}
     */
    public List<CircleMember> responders() {
        return members.stream().filter(m -> m.role() == MemberRole.RESPONDER).toList();
    }

    /**
     * What still needs doing before SafeWord actually protects anyone.
     *
     * @param today the day to assess
     * @return outstanding setup steps, empty when complete
     */
    public List<String> outstandingSetupSteps(LocalDate today) {
        List<String> steps = new java.util.ArrayList<>();
        if (!passphraseStatus(today).isProtectionActive()) {
            steps.add("Agree a family passphrase in person. Until you do, SafeWord is not "
                    + "protecting you - it is just an app on a phone.");
        }
        if (responders().isEmpty()) {
            steps.add("Add at least one person who can be called for help.");
        }
        if (passphraseStatus(today) == PassphraseStatus.NEEDS_REFRESH) {
            steps.add("It has been a while since you agreed your passphrase. Have a quick "
                    + "practice so everyone still remembers it.");
        }
        return steps;
    }
}
