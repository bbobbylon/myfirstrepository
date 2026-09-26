package com.safeword.pause;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.safeword.escalate.EscalationComposer;

/**
 * Tests for the pause screen and the escalation copy.
 *
 * <p>These assert on <b>wording</b>, because the pause screen is the product. Its job is to
 * be read and believed by a frightened person in a few seconds, and a change that makes it
 * longer, softer or cleverer would make it worse.
 */
class PauseAndEscalationTest {

    private final PauseChecklist checklist = new PauseChecklist();
    private final EscalationComposer escalation = new EscalationComposer();

    @Test
    @DisplayName("the headline reassures rather than alarms")
    void headlineReassures() {
        // Adding to someone's panic is counterproductive: panic is the attacker's tool.
        assertThat(checklist.headline()).contains("Take your time");
    }

    @Test
    @DisplayName("the checklist states the gift card rule absolutely")
    void giftCardRuleIsAbsolute() {
        assertThat(checklist.items())
                .anySatisfy(item -> assertThat(item.statement()).contains("gift cards"));
        assertThat(checklist.items().getFirst().detail()).contains("Never");
    }

    @Test
    @DisplayName("the checklist says a familiar voice no longer proves identity")
    void checklistAddressesVoiceCloning() {
        // The single heuristic that has to be replaced.
        assertThat(checklist.items())
                .anySatisfy(item -> assertThat(item.statement())
                        .contains("familiar voice no longer proves"));
    }

    @Test
    @DisplayName("the checklist names the secrecy request as the biggest warning sign")
    void checklistNamesSecrecy() {
        assertThat(checklist.items())
                .anySatisfy(item -> assertThat(item.statement()).contains("Don't tell anyone"));
    }

    @Test
    @DisplayName("every item is short enough to read at a glance")
    void itemsAreShort() {
        // One idea per line. A paragraph on this screen is a paragraph nobody reads.
        assertThat(checklist.items()).allSatisfy(item ->
                assertThat(item.statement().length()).isLessThan(80));
    }

    @Test
    @DisplayName("most items hold regardless of what the caller claims")
    void mostItemsAreAbsolute() {
        assertThat(checklist.items()).filteredOn(PauseChecklist.ChecklistItem::isAbsolute)
                .hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("the escalation message does NOT say the person is being scammed")
    void escalationDoesNotEmbarrass() {
        // If the call turns out to be genuine, nobody has been embarrassed - which is what
        // keeps the feature cheap enough to use again. A tool that costs dignity gets used once.
        String message = escalation.composeToResponders("Mum", true);

        assertThat(message).contains("has asked for a call");
        assertThat(message.toLowerCase())
                .doesNotContain("scam")
                .doesNotContain("fraud")
                .doesNotContain("victim");
    }

    @Test
    @DisplayName("the escalation message tells responders how to help")
    void escalationCoachesResponders() {
        String message = escalation.composeToResponders("Mum", true);

        assertThat(message).contains("stay calm");
        assertThat(message).contains("don't tell them off");
        assertThat(message).contains("call the person or company back");
    }

    @Test
    @DisplayName("mentioning money changes the wording, so responders know the urgency")
    void moneyChangesTheWording() {
        assertThat(escalation.composeToResponders("Mum", true)).contains("about money");
        assertThat(escalation.composeToResponders("Mum", false)).contains("second opinion");
    }

    @Test
    @DisplayName("an empty circle admits nobody was reached")
    void emptyCircleAdmitsFailure() {
        // Claiming we told someone when there is nobody to tell would leave a person waiting
        // for a call that is never coming.
        String confirmation = escalation.composeConfirmation(0);

        assertThat(confirmation).contains("could not reach anyone");
        assertThat(confirmation).contains("always safe to do");
    }

    @Test
    @DisplayName("the confirmation gives something useful to do while waiting")
    void confirmationBridgesTheWait() {
        String confirmation = escalation.composeConfirmation(2);

        assertThat(confirmation).contains("2 people");
        assertThat(confirmation).contains("you can hang up");
    }
}
