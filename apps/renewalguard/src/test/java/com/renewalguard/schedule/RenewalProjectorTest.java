package com.renewalguard.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.BenefitProgram;
import com.renewalguard.domain.EnrollmentCategory;
import com.renewalguard.domain.RenewalUrgency;

/** Tests for deadline maths, with time frozen so the assertions stay true. */
class RenewalProjectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 20);

    private final RenewalProjector projector = new RenewalProjector(
            Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC));

    private BenefitCase dueIn(long days) {
        return new BenefitCase("c1", "u1", BenefitProgram.MEDICAID, "CA",
                EnrollmentCategory.EXPANSION_ADULT, TODAY.plusDays(days), null, null);
    }

    @Test
    @DisplayName("today comes from the injected clock")
    void todayComesFromClock() {
        assertThat(projector.today()).isEqualTo(TODAY);
    }

    @ParameterizedTest(name = "due in {0} days -> {1}")
    @CsvSource({
            " -5, CRITICAL",
            "  0, CRITICAL",
            "  1, CRITICAL",
            " 10, CRITICAL",
            " 11, HIGH",
            " 30, HIGH",
            " 31, MEDIUM",
            " 60, MEDIUM",
            " 61, WATCH",
            "200, WATCH"
    })
    @DisplayName("urgency thresholds are exact")
    void urgencyThresholdsAreExact(long days, RenewalUrgency expected) {
        assertThat(projector.urgency(dueIn(days))).isEqualTo(expected);
    }

    @Test
    @DisplayName("a PASSED deadline is CRITICAL, not dismissed")
    void passedDeadlineIsCritical() {
        // Unlike RefillRadar, where a passed date usually meant stale data, here it is the
        // actual emergency - and reinstatement is often still possible if acted on fast.
        // Going quiet at the moment of failure would be the worst possible behaviour.
        assertThat(projector.urgency(dueIn(-30))).isEqualTo(RenewalUrgency.CRITICAL);
    }

    @Test
    @DisplayName("documents-ready date works backwards from the deadline")
    void documentsReadyWorksBackwards() {
        // The deadline is not really the deadline: if the agency asks for a pay stub with
        // 10 days to respond, you need it BEFORE the request arrives.
        BenefitCase benefitCase = dueIn(60);

        assertThat(projector.documentsReadyBy(benefitCase, 10))
                .isEqualTo(benefitCase.renewalDueOn().minusDays(10));
        assertThat(projector.documentsReadyBy(benefitCase, 30))
                .isEqualTo(benefitCase.renewalDueOn().minusDays(30));
    }

    @Test
    @DisplayName("an address never confirmed always prompts")
    void neverConfirmedAlwaysPrompts() {
        assertThat(projector.needsAddressCheck(dueIn(90))).isTrue();
    }

    @Test
    @DisplayName("a recently confirmed address does not prompt; a stale one does")
    void addressStalenessIsRespected() {
        BenefitCase recent = new BenefitCase("c2", "u1", BenefitProgram.MEDICAID, "CA",
                EnrollmentCategory.CHILD, TODAY.plusDays(90), null, TODAY.minusDays(30));
        BenefitCase stale = new BenefitCase("c3", "u1", BenefitProgram.MEDICAID, "CA",
                EnrollmentCategory.CHILD, TODAY.plusDays(90), null, TODAY.minusDays(400));

        assertThat(projector.needsAddressCheck(recent)).isFalse();
        assertThat(projector.needsAddressCheck(stale)).isTrue();
    }

    @Test
    @DisplayName("every urgency routes to a process step, never to an eligibility opinion")
    void guidanceNeverJudgesEligibility() {
        // Guards the line that keeps this a deadline tracker rather than something
        // pretending to determine eligibility.
        for (RenewalUrgency urgency : RenewalUrgency.values()) {
            String text = (urgency.headline() + " " + urgency.guidance()).toLowerCase();
            assertThat(text)
                    .doesNotContain("you qualify")
                    .doesNotContain("you do not qualify")
                    .doesNotContain("you are eligible")
                    .doesNotContain("not eligible");
        }
    }
}
