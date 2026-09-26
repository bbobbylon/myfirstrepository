package com.safeword.passphrase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.safeword.domain.CircleMember;
import com.safeword.domain.FamilyCircle;
import com.safeword.domain.MemberRole;

/**
 * Tests for the passphrase protocol, including the security invariant that defines SafeWord.
 *
 * <p>The central promise - <b>this server never learns the passphrase</b> - is only worth
 * anything if it is enforced rather than intended. These tests are what stop a future
 * "convenience" feature from quietly starting to collect secrets.
 */
class PassphraseProtocolTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

    private final PassphraseProtocol protocol = new PassphraseProtocol();

    @ParameterizedTest
    @ValueSource(strings = {"bluebird", "our word", "   x   ", "123456"})
    @DisplayName("ANY supplied secret is refused, whatever it looks like")
    void refusesAnySuppliedSecret(String supplied) {
        // If it never touches our servers, a breach of our servers cannot leak it.
        assertThat(protocol.rejectsSuppliedSecret(supplied)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("an absent secret is fine - that is the expected state")
    void absentSecretIsFine(String supplied) {
        assertThat(protocol.rejectsSuppliedSecret(supplied)).isFalse();
    }

    @Test
    @DisplayName("the refusal explains itself rather than returning a bare error")
    void refusalExplainsItself() {
        // The reader may be a worried adult child who thought they were being helpful.
        assertThat(protocol.secretRejectionMessage())
                .contains("never store it")
                .contains("never leak it");
    }

    @Test
    @DisplayName("agreement instructions insist on in-person, and forbid writing it down")
    void instructionsInsistOnInPerson() {
        String instructions = protocol.agreementInstructions();

        // A passphrase agreed over a channel an attacker already controls is not a secret.
        assertThat(instructions).contains("IN PERSON");
        assertThat(instructions).contains("Never agree it over a call that came to you");
        assertThat(instructions).contains("Do not write it down");
    }

    @Test
    @DisplayName("the usage rule says ASK, never volunteer")
    void usageRuleSaysAskNotTell() {
        // "Your grandson told me the word is..." is the obvious counter-attack, and the
        // defence is behavioural: you ask for it, you never say it first.
        assertThat(protocol.agreementInstructions()).contains("you ASK the caller for the word");
        assertThat(protocol.usageRule()).contains("Do not say it first");
    }

    @Test
    @DisplayName("a circle with no agreed passphrase is NOT set up, however complete it looks")
    void circleWithoutPassphraseIsNotSetUp() {
        // Someone who installs SafeWord and never agrees a passphrase is arguably WORSE off
        // than before, because they now believe they are protected.
        FamilyCircle circle = new FamilyCircle("c1", "The Family", null,
                List.of(new CircleMember("m1", "Dad", MemberRole.PROTECTED_PERSON, null),
                        new CircleMember("m2", "Ana", MemberRole.RESPONDER, "+15551234567")));

        assertThat(circle.passphraseStatus(TODAY)).isEqualTo(PassphraseStatus.NOT_AGREED);
        assertThat(circle.isSetupComplete(TODAY)).isFalse();
        assertThat(circle.outstandingSetupSteps(TODAY))
                .anySatisfy(step -> assertThat(step).contains("not protecting you"));
    }

    @Test
    @DisplayName("a circle with a passphrase and a responder is set up")
    void completeCircleIsSetUp() {
        FamilyCircle circle = new FamilyCircle("c1", "The Family", TODAY.minusDays(30),
                List.of(new CircleMember("m1", "Ana", MemberRole.RESPONDER, "+15551234567")));

        assertThat(circle.passphraseStatus(TODAY)).isEqualTo(PassphraseStatus.AGREED);
        assertThat(circle.isSetupComplete(TODAY)).isTrue();
        assertThat(circle.outstandingSetupSteps(TODAY)).isEmpty();
    }

    @Test
    @DisplayName("an old passphrase prompts a practice rather than failing")
    void oldPassphrasePromptsPractice() {
        // A passphrase only works if remembered under stress, and memory of something never
        // used decays. This is a nudge, not an error.
        FamilyCircle circle = new FamilyCircle("c1", "The Family",
                TODAY.minusDays(PassphraseProtocol.REFRESH_AFTER_DAYS + 1),
                List.of(new CircleMember("m1", "Ana", MemberRole.RESPONDER, "+15551234567")));

        assertThat(circle.passphraseStatus(TODAY)).isEqualTo(PassphraseStatus.NEEDS_REFRESH);
        assertThat(circle.passphraseStatus(TODAY).isProtectionActive()).isTrue();
        assertThat(circle.outstandingSetupSteps(TODAY))
                .anySatisfy(step -> assertThat(step).contains("practice"));
    }

    @Test
    @DisplayName("a circle with a passphrase but nobody to call is incomplete")
    void circleWithoutRespondersIsIncomplete() {
        FamilyCircle circle = new FamilyCircle("c1", "The Family", TODAY.minusDays(10),
                List.of(new CircleMember("m1", "Dad", MemberRole.PROTECTED_PERSON, null)));

        assertThat(circle.isSetupComplete(TODAY)).isFalse();
        assertThat(circle.outstandingSetupSteps(TODAY))
                .anySatisfy(step -> assertThat(step).contains("at least one person"));
    }

    @Test
    @DisplayName("a responder with no contact route is rejected at construction")
    void responderMustBeReachable() {
        // Silently unreachable at the one moment they are needed is the worst failure mode.
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new CircleMember("m1", "Ana", MemberRole.RESPONDER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("could never be reached");
    }

    @Test
    @DisplayName("the FamilyCircle record has no field that could hold a passphrase")
    void circleHasNoSecretField() {
        // Structural assertion: if someone adds a `passphrase` field to the record, this
        // fails and they have to justify it. Cheap insurance on the core promise.
        assertThat(FamilyCircle.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "name", "passphraseAgreedOn", "members")
                .doesNotContain("passphrase", "secret", "passphraseHash");
    }
}
