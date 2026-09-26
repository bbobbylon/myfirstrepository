package com.renewalguard.remind;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.renewalguard.checklist.DocumentChecklist;
import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.BenefitProgram;
import com.renewalguard.domain.EnrollmentCategory;
import com.renewalguard.domain.RenewalUrgency;

/**
 * Tests for the reminder ladder and the words it produces.
 *
 * <p>Like RefillRadar's {@code AlertComposerTest}, several assertions are about
 * <b>wording</b>. The line between "deadline tracker" and "claims to decide eligibility" is
 * drawn by the sentences this code emits, so those rules are encoded as tests rather than
 * trusted to code review.
 */
class ReminderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

    private final ReminderLadder ladder = new ReminderLadder();
    private final ReminderComposer composer = new ReminderComposer();
    private final DocumentChecklist checklist = new DocumentChecklist();

    private BenefitCase dueIn(long days) {
        return new BenefitCase("c1", "u1", BenefitProgram.MEDICAID, "CA",
                EnrollmentCategory.EXPANSION_ADULT, TODAY.plusDays(days), null, null);
    }

    @ParameterizedTest
    @ValueSource(longs = {60, 45, 30, 21, 14, 10})
    @DisplayName("each milestone gets a reminder")
    void milestonesRemind(long days) {
        assertThat(ladder.shouldRemindToday(days)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(longs = {59, 50, 40, 25, 15, 11})
    @DisplayName("non-milestone days outside the daily phase stay quiet")
    void nonMilestonesAreQuiet(long days) {
        // A reminder every day for two months trains people to ignore it - and an ignored
        // reminder is worse than none, because it also carries false reassurance.
        assertThat(ladder.shouldRemindToday(days)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(longs = {10, 5, 1, 0, -1, -30})
    @DisplayName("inside the final window, and after it, reminds every day")
    void dailyPhaseRemindsDaily(long days) {
        assertThat(ladder.shouldRemindToday(days)).isTrue();
        assertThat(ladder.isDailyPhase(days)).isTrue();
    }

    @Test
    @DisplayName("the full ladder is published so the cadence is predictable")
    void ladderIsPublished() {
        // Descending, because these are DAYS BEFORE the deadline: 60 days out comes first
        // in time, and is the largest number. isSorted() would assert ascending and fail.
        assertThat(ladder.milestones())
                .containsExactly(60, 45, 30, 21, 14, 10)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @ParameterizedTest
    @CsvSource({"5, CRITICAL", "20, HIGH", "45, MEDIUM", "120, WATCH"})
    @DisplayName("subject lines reflect urgency without alarm")
    void subjectsReflectUrgency(long days, RenewalUrgency urgency) {
        String subject = composer.composeSubject(dueIn(days), urgency, days);

        assertThat(subject).contains("Medicaid").isNotBlank();
        assertThat(subject.toLowerCase()).doesNotContain("urgent!").doesNotContain("warning!");
    }

    @Test
    @DisplayName("a passed deadline says it may still be fixable")
    void passedDeadlineOffersHope() {
        // The single most important paragraph in the app: people assume a passed deadline is
        // final and stop trying. Often it is not.
        String body = body(dueIn(-5));

        assertThat(body).contains("does NOT automatically mean you have lost coverage");
        assertThat(body).contains("Call your state agency");
    }

    @Test
    @DisplayName("the body NEVER says whether someone qualifies")
    void neverJudgesEligibility() {
        String body = body(dueIn(15)).toLowerCase();

        assertThat(body)
                .doesNotContain("you qualify")
                .doesNotContain("you do not qualify")
                .doesNotContain("you may not be eligible")
                .doesNotContain("you are eligible");
    }

    @Test
    @DisplayName("the body never asks for sensitive data, and says it never will")
    void neverAsksForSensitiveData() {
        // This audience is heavily targeted by benefits scams. A legitimate service that
        // behaves like a scam teaches exactly the wrong reflex.
        String body = body(dueIn(15));

        assertThat(body).contains("never ask you for your Social Security number");
        assertThat(body.toLowerCase()).doesNotContain("reply with your ssn");
    }

    @Test
    @DisplayName("the body defers to the user's own notice")
    void defersToTheNotice() {
        assertThat(body(dueIn(15))).contains("believe the notice");
    }

    @Test
    @DisplayName("the address prompt appears when the address is unconfirmed")
    void addressPromptAppears() {
        // Undelivered mail is one of the largest causes of procedural termination, and this
        // prompt is close to free.
        assertThat(body(dueIn(45))).contains("Have you moved");
    }

    @Test
    @DisplayName("the checklist tells people where to look, not just what to find")
    void checklistIsActionable() {
        BenefitCase benefitCase = dueIn(30);

        assertThat(checklist.forCase(benefitCase)).isNotEmpty().allSatisfy(document -> {
            assertThat(document.label()).isNotBlank();
            assertThat(document.whereToFind()).isNotBlank();
        });
        assertThat(checklist.commonlyMissed(benefitCase)).isNotEmpty();
    }

    @Test
    @DisplayName("the renewal notice itself is on the checklist")
    void noticeIsOnTheChecklist() {
        // It states the real deadline and the real document list, so it outranks anything
        // this app assumes.
        assertThat(checklist.forCase(dueIn(30)))
                .anySatisfy(d -> assertThat(d.label()).contains("renewal notice"));
    }

    private String body(BenefitCase benefitCase) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(TODAY, benefitCase.renewalDueOn());
        return composer.composeBody(benefitCase, RenewalUrgency.fromDaysUntilDue(days), days,
                checklist.forCase(benefitCase), true, "We assume 10 days. Check it.");
    }

    @Test
    @DisplayName("a child case gets the school-enrolment document, an adult case does not")
    void checklistVariesByCategory() {
        BenefitCase child = new BenefitCase("c2", "u1", BenefitProgram.MEDICAID, "CA",
                EnrollmentCategory.CHILD, TODAY.plusDays(30), null, null);

        assertThat(checklist.forCase(child))
                .anySatisfy(d -> assertThat(d.label()).contains("school enrolment"));
        assertThat(checklist.forCase(dueIn(30)))
                .noneSatisfy(d -> assertThat(d.label()).contains("school enrolment"));
    }

    @Test
    @DisplayName("documents listed are framed as commonly asked for, not as guaranteed")
    void documentsAreNotPresentedAsDefinitive() {
        List<String> labels = checklist.forCase(dueIn(30)).stream()
                .map(d -> d.label().toLowerCase()).toList();

        assertThat(labels).isNotEmpty();
        assertThat(String.join(" ", labels)).doesNotContain("required by law");
    }
}
