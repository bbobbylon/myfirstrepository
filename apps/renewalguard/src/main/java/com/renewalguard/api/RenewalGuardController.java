package com.renewalguard.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.renewalguard.checklist.DocumentChecklist;
import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.RenewalUrgency;
import com.renewalguard.remind.ReminderComposer;
import com.renewalguard.remind.ReminderLadder;
import com.renewalguard.rules.RenewalCadence;
import com.renewalguard.rules.ResponseWindow;
import com.renewalguard.schedule.RenewalProjector;
import com.renewalguard.store.BenefitCaseRepository;

import jakarta.validation.Valid;

/**
 * HTTP API for tracking cases and checking renewal status.
 *
 * <p>Thin by design - it translates HTTP to the domain and nothing else.
 *
 * <p><b>No authentication in v0.1.</b> {@code userId} is trusted as supplied. A benefits case
 * reveals that someone is on Medicaid in a given state, which is sensitive on its own. This
 * must be fixed before exposure, and is stated here in the code rather than left implicit.
 */
@RestController
@RequestMapping("/api")
public class RenewalGuardController {

    private final BenefitCaseRepository cases;
    private final RenewalProjector projector;
    private final RenewalCadence cadence;
    private final ResponseWindow responseWindow;
    private final ReminderLadder ladder;
    private final DocumentChecklist checklist;
    private final ReminderComposer composer;

    /**
     * @param cases          case storage
     * @param projector      date and urgency maths
     * @param cadence        renewal frequency rules
     * @param responseWindow response-window assumptions
     * @param ladder         reminder schedule
     * @param checklist      document checklist
     * @param composer       message copy
     */
    public RenewalGuardController(BenefitCaseRepository cases,
                                  RenewalProjector projector,
                                  RenewalCadence cadence,
                                  ResponseWindow responseWindow,
                                  ReminderLadder ladder,
                                  DocumentChecklist checklist,
                                  ReminderComposer composer) {
        this.cases = cases;
        this.projector = projector;
        this.cadence = cadence;
        this.responseWindow = responseWindow;
        this.ladder = ladder;
        this.checklist = checklist;
        this.composer = composer;
    }

    /** Response-window days supplied per case, kept alongside storage in v0.1. */
    private final java.util.Map<String, Integer> suppliedWindows = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Starts tracking a benefit case.
     *
     * @param request the case to track; validated before this method runs
     * @return {@code 201 Created} with the stored case
     */
    @PostMapping("/cases")
    public ResponseEntity<BenefitCase> trackCase(@Valid @RequestBody BenefitCaseRequest request) {
        BenefitCase benefitCase = new BenefitCase(
                UUID.randomUUID().toString(),
                request.userId(),
                request.program(),
                request.stateCode().toUpperCase(java.util.Locale.ROOT),
                request.category(),
                request.renewalDueOn(),
                request.noticeReceivedOn(),
                request.addressConfirmedOn());

        if (request.responseWindowDays() != null) {
            suppliedWindows.put(benefitCase.id(), request.responseWindowDays());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(cases.save(benefitCase));
    }

    /**
     * Lists a user's tracked cases.
     *
     * @param userId the owner
     * @return the cases, possibly empty
     */
    @GetMapping("/users/{userId}/cases")
    public List<BenefitCase> listCases(@PathVariable String userId) {
        return cases.findByUserId(userId);
    }

    /**
     * The main endpoint: full renewal status for one case.
     *
     * @param caseId the case
     * @return the status, or {@code 404} if unknown
     */
    @GetMapping("/cases/{caseId}/status")
    public ResponseEntity<RenewalStatusResponse> status(@PathVariable String caseId) {
        return cases.findById(caseId).map(this::toStatus).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * The composed reminder message for a case, as it would be sent.
     *
     * <p>Exposed so the copy can be reviewed without waiting for a send, and so tests can
     * assert on the words a real person would receive.
     *
     * @param caseId the case
     * @return subject and body, or {@code 404} if unknown
     */
    @GetMapping("/cases/{caseId}/reminder")
    public ResponseEntity<ReminderPreview> reminder(@PathVariable String caseId) {
        return cases.findById(caseId).map(benefitCase -> {
            long days = projector.daysUntilDue(benefitCase);
            RenewalUrgency urgency = RenewalUrgency.fromDaysUntilDue(days);
            Integer supplied = suppliedWindows.get(benefitCase.id());

            return ResponseEntity.ok(new ReminderPreview(
                    composer.composeSubject(benefitCase, urgency, days),
                    composer.composeBody(benefitCase, urgency, days,
                            checklist.forCase(benefitCase),
                            projector.needsAddressCheck(benefitCase),
                            responseWindow.explain(supplied)),
                    ladder.shouldRemindToday(days)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Assembles the full status view for a case.
     *
     * @param benefitCase the case
     * @return the populated response
     */
    private RenewalStatusResponse toStatus(BenefitCase benefitCase) {
        long days = projector.daysUntilDue(benefitCase);
        RenewalUrgency urgency = RenewalUrgency.fromDaysUntilDue(days);
        Integer supplied = suppliedWindows.get(benefitCase.id());
        int windowDays = responseWindow.planningWindowDays(supplied);

        List<String> caveats = new ArrayList<>();
        caveats.add("Your own renewal notice is the authority on your deadline. If it "
                + "disagrees with this date, believe the notice.");
        caveats.add("RenewalGuard tracks deadlines and paperwork. It does NOT decide whether "
                + "you qualify - only your state agency does that.");

        if (cadence.affectedBySixMonthChange(benefitCase)) {
            caveats.add("From 2027, adults covered through Medicaid expansion renew every "
                    + "SIX months instead of once a year. Your next renewal after this one "
                    + "is projected sooner than you may expect.");
        }
        if (benefitCase.category().isUnspecified()) {
            caveats.add("You have not told us which Medicaid category you are in, so we "
                    + "assumed an annual renewal. If you are an adult covered through "
                    + "expansion, your cadence may change in 2027 - tell us to be sure.");
        }
        if (!benefitCase.cadenceIsModelled()) {
            caveats.add("Renewal rules for " + benefitCase.program().label()
                    + " are not modelled yet. Dates shown come only from what you entered.");
        }

        return new RenewalStatusResponse(
                benefitCase.id(),
                benefitCase.program().label(),
                benefitCase.stateCode(),
                benefitCase.category().label(),
                projector.today(),
                benefitCase.renewalDueOn(),
                days,
                urgency.name(),
                urgency.headline(),
                urgency.guidance(),
                projector.documentsReadyBy(benefitCase, windowDays),
                projector.needsAddressCheck(benefitCase),
                ladder.shouldRemindToday(days),
                ladder.milestones(),
                cadence.projectFollowingRenewal(benefitCase),
                cadence.cadenceMonths(benefitCase.category(), benefitCase.renewalDueOn()),
                cadence.affectedBySixMonthChange(benefitCase),
                responseWindow.explain(supplied),
                checklist.forCase(benefitCase),
                caveats);
    }

    /**
     * A composed reminder, ready to send.
     *
     * @param subject      the subject line
     * @param body         the message body
     * @param wouldSendToday whether today is a reminder day
     */
    public record ReminderPreview(String subject, String body, boolean wouldSendToday) {
    }
}
