package com.renewalguard.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commonauth.web.AccountPrincipal;
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
 * <p><b>v0.2 closed the hole v0.1 documented right here.</b> The old comment said
 * "{@code userId} is trusted as supplied", and it meant it: {@code POST /api/cases} took the
 * owner from the request body, and {@code GET /api/cases/&#123;caseId&#125;/status} checked
 * nothing at all. Anyone who held or guessed a case id could read that a named account is
 * enrolled in Medicaid, in a named state, in a named eligibility category - and could file
 * cases under anybody's id.
 *
 * <p>The fix is removal rather than validation, the same move RefillRadar made in its v0.4:
 *
 * <pre>
 * v0.1   POST /api/cases            &#123;"userId": "...", ...&#125;   &#8592; client picks the owner
 * v0.2   POST /api/cases            &#123;...&#125;                    &#8592; the session does
 *
 * v0.1   GET  /api/users/&#123;userId&#125;/cases                    &#8592; client picks whose
 * v0.2   GET  /api/me/cases                                 &#8592; the session does
 *
 * v0.1   GET  /api/cases/&#123;caseId&#125;/status                   &#8592; no check whatsoever
 * v0.2   GET  /api/me/cases/&#123;caseId&#125;/status                &#8592; owner is in the query
 * </pre>
 *
 * <p>A case id still appears in the last path, because unlike SafeWord - where one account
 * has exactly one circle - a person here genuinely tracks several cases and has to be able to
 * name one. So this is not the "delete the identifier" fix; it is the other one: the
 * identifier stays, and every lookup takes the owner alongside it, which
 * {@link BenefitCaseRepository} enforces by not offering a method that omits it.
 *
 * <p><b>404, never 403, for somebody else's case.</b> A 403 would confirm the id is real, and
 * "real, just not yours" is exactly what an attacker enumerating ids wants to learn. The
 * indistinguishable answer is the point, so {@code notFound()} below is a security decision
 * rather than laziness about error codes.
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

    /**
     * Starts tracking a benefit case, owned by whoever is logged in.
     *
     * @param request   the case to track; validated before this method runs
     * @param principal the authenticated account, resolved from the session cookie
     * @return {@code 201 Created} with the stored case
     */
    @PostMapping("/cases")
    public ResponseEntity<BenefitCase> trackCase(@Valid @RequestBody BenefitCaseRequest request,
                                                 @AuthenticationPrincipal AccountPrincipal principal) {
        BenefitCase benefitCase = new BenefitCase(
                UUID.randomUUID().toString(),
                // From the session, not the payload. BenefitCaseRequest no longer has a
                // userId field to read even if a future edit tried to.
                principal.accountId(),
                request.program(),
                request.stateCode().toUpperCase(java.util.Locale.ROOT),
                request.category(),
                request.renewalDueOn(),
                request.noticeReceivedOn(),
                request.addressConfirmedOn(),
                // Stored on the row now. In v0.1 this went into a map on this class, so it
                // was lost on restart while the case survived, and two replicas disagreed.
                request.responseWindowDays());

        return ResponseEntity.status(HttpStatus.CREATED).body(cases.save(benefitCase));
    }

    /**
     * Lists the logged-in user's own tracked cases.
     *
     * <p>Was {@code /api/users/&#123;userId&#125;/cases}, which let any caller list anyone's
     * cases by editing the URL. There is no path variable to edit now.
     *
     * @param principal the authenticated account
     * @return their cases, possibly empty
     */
    @GetMapping("/me/cases")
    public List<BenefitCase> listMyCases(@AuthenticationPrincipal AccountPrincipal principal) {
        return cases.findByUserId(principal.accountId());
    }

    /**
     * The main endpoint: full renewal status for one of your own cases.
     *
     * @param caseId    the case
     * @param principal the authenticated account, which must own it
     * @return the status, or {@code 404} if the case is unknown <em>or</em> not theirs - the
     *         two are deliberately indistinguishable
     */
    @GetMapping("/me/cases/{caseId}/status")
    public ResponseEntity<RenewalStatusResponse> status(@PathVariable String caseId,
                                                        @AuthenticationPrincipal AccountPrincipal principal) {
        return cases.findByIdAndUserId(caseId, principal.accountId())
                .map(this::toStatus).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Stops tracking one of your own cases.
     *
     * <p>New in v0.2, and owner-scoped from birth rather than retrofitted. Worth stating why
     * that ordering matters: RefillRadar shipped a {@code DELETE} that "checked nothing at
     * all", so any caller could delete any user's medication. A destructive endpoint is the
     * worst place to discover a missing ownership check, because the evidence is what it
     * destroyed.
     *
     * @param caseId    the case to stop tracking
     * @param principal the authenticated account, which must own it
     * @return {@code 204 No Content} on success, or {@code 404} if the case is unknown
     *         <em>or</em> not theirs - indistinguishable, for the same reason as the reads
     */
    @DeleteMapping("/me/cases/{caseId}")
    public ResponseEntity<Void> stopTracking(@PathVariable String caseId,
                                             @AuthenticationPrincipal AccountPrincipal principal) {
        return cases.deleteByIdAndUserId(caseId, principal.accountId())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * The composed reminder message for a case, as it would be sent.
     *
     * <p>Exposed so the copy can be reviewed without waiting for a send, and so tests can
     * assert on the words a real person would receive.
     *
     * @param caseId    the case
     * @param principal the authenticated account, which must own it
     * @return subject and body, or {@code 404} if unknown or not theirs
     */
    @GetMapping("/me/cases/{caseId}/reminder")
    public ResponseEntity<ReminderPreview> reminder(@PathVariable String caseId,
                                                    @AuthenticationPrincipal AccountPrincipal principal) {
        return cases.findByIdAndUserId(caseId, principal.accountId()).map(benefitCase -> {
            long days = projector.daysUntilDue(benefitCase);
            RenewalUrgency urgency = RenewalUrgency.fromDaysUntilDue(days);
            Integer supplied = benefitCase.responseWindowDays();

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
        Integer supplied = benefitCase.responseWindowDays();
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
