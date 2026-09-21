package com.safeword.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.safeword.domain.CircleMember;
import com.safeword.domain.FamilyCircle;
import com.safeword.domain.MemberRole;
import com.safeword.escalate.EscalationComposer;
import com.safeword.passphrase.PassphraseProtocol;
import com.safeword.pause.PauseChecklist;
import com.safeword.scam.PressureSignal;
import com.safeword.scam.RiskAssessment;
import com.safeword.scam.RiskScorer;
import com.safeword.scam.ScamPattern;
import com.safeword.scam.ScamPatternLibrary;
import com.safeword.store.CircleRepository;

import jakarta.validation.Valid;

/**
 * HTTP API for circles, the pause screen, risk checks and escalation.
 *
 * <p><b>No authentication in v0.1.</b> Circle ids are trusted as supplied. A circle names real
 * people and their phone numbers, so this must be fixed before exposure.
 *
 * <p>Note what has no endpoint: there is <b>no way to send SafeWord a passphrase</b>. That is
 * not an oversight, it is the design. {@link #createCircle} actively rejects any attempt.
 */
@RestController
@RequestMapping("/api")
public class SafeWordController {

    private final CircleRepository circles;
    private final PassphraseProtocol protocol;
    private final PauseChecklist pauseChecklist;
    private final RiskScorer scorer;
    private final ScamPatternLibrary library;
    private final EscalationComposer escalation;
    private final Clock clock;

    /**
     * @param circles        circle storage
     * @param protocol       passphrase rules and the no-secrets guard
     * @param pauseChecklist the pause screen content
     * @param scorer         risk assessment
     * @param library        scam pattern library
     * @param escalation     escalation message copy
     * @param clock          supplies today's date
     */
    public SafeWordController(CircleRepository circles,
                              PassphraseProtocol protocol,
                              PauseChecklist pauseChecklist,
                              RiskScorer scorer,
                              ScamPatternLibrary library,
                              EscalationComposer escalation,
                              Clock clock) {
        this.circles = circles;
        this.protocol = protocol;
        this.pauseChecklist = pauseChecklist;
        this.scorer = scorer;
        this.library = library;
        this.escalation = escalation;
        this.clock = clock;
    }

    /**
     * Creates a family circle.
     *
     * <p>Rejects the request outright if it carries anything in the {@code passphrase} field.
     * That field exists in the request type only so a client sending one gets a clear
     * explanation instead of silence - and so the refusal is testable.
     *
     * @param request the circle to create
     * @return {@code 201 Created}, or {@code 400} with an explanation if a secret was sent
     */
    @PostMapping("/circles")
    public ResponseEntity<?> createCircle(@Valid @RequestBody CircleRequest request) {
        if (protocol.rejectsSuppliedSecret(request.passphrase())) {
            return ResponseEntity.badRequest()
                    .body(new Refusal("passphrase_not_accepted", protocol.secretRejectionMessage()));
        }

        List<CircleMember> members = request.members() == null ? List.of()
                : request.members().stream()
                        .map(m -> new CircleMember(UUID.randomUUID().toString(), m.name(),
                                m.role() == null ? MemberRole.RESPONDER : m.role(), m.contact()))
                        .toList();

        FamilyCircle circle = new FamilyCircle(
                UUID.randomUUID().toString(), request.name(),
                request.passphraseAgreedOn(), members);

        return ResponseEntity.status(HttpStatus.CREATED).body(circles.save(circle));
    }

    /**
     * Setup status for a circle, including what is still outstanding.
     *
     * @param circleId the circle
     * @return the status, or {@code 404} if unknown
     */
    @GetMapping("/circles/{circleId}/status")
    public ResponseEntity<CircleStatus> circleStatus(@PathVariable String circleId) {
        LocalDate today = LocalDate.now(clock);
        return circles.findById(circleId)
                .map(circle -> ResponseEntity.ok(new CircleStatus(
                        circle.id(),
                        circle.name(),
                        circle.passphraseStatus(today).name(),
                        circle.passphraseStatus(today).explanation(),
                        circle.isSetupComplete(today),
                        circle.outstandingSetupSteps(today),
                        circle.responders().size(),
                        protocol.usageRule())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * The passphrase agreement instructions.
     *
     * @return the instructions
     */
    @GetMapping("/passphrase/instructions")
    public Instructions passphraseInstructions() {
        return new Instructions(protocol.agreementInstructions(), protocol.usageRule(),
                protocol.secretRejectionMessage());
    }

    /**
     * The pause screen content.
     *
     * @return the headline and checklist
     */
    @GetMapping("/pause")
    public PauseScreen pause() {
        return new PauseScreen(pauseChecklist.headline(), pauseChecklist.items());
    }

    /**
     * Assesses a call from the tactics the user reported.
     *
     * @param request the reported tactics
     * @return the assessment with its caveat
     */
    @PostMapping("/check-call")
    public CallCheck checkCall(@RequestBody CallCheckRequest request) {
        Set<PressureSignal> signals = request.signals() == null ? Set.of() : request.signals();
        RiskAssessment assessment = scorer.assess(signals);

        return new CallCheck(
                assessment.score(),
                assessment.level().name(),
                assessment.level().headline(),
                assessment.level().action(),
                scorer.hasStandaloneRedFlag(signals),
                assessment.likelyPatterns(),
                assessment.confidenceCaveat());
    }

    /**
     * Raises a request for help to a circle's responders.
     *
     * <p>v0.1 composes the messages and reports who would be contacted; it does not send
     * push or SMS. The response says so, because someone who wrongly believes help is coming
     * is worse off than someone who knows it is not.
     *
     * @param circleId  the circle
     * @param request   what the call is about
     * @return the escalation result, or {@code 404} if the circle is unknown
     */
    @PostMapping("/circles/{circleId}/escalate")
    public ResponseEntity<EscalationResult> escalate(@PathVariable String circleId,
                                                     @RequestBody EscalateRequest request) {
        return circles.findById(circleId).map(circle -> {
            List<CircleMember> responders = circle.responders();
            String protectedName = circle.members().stream()
                    .filter(m -> m.role() == MemberRole.PROTECTED_PERSON)
                    .map(CircleMember::name).findFirst().orElse(circle.name());

            return ResponseEntity.ok(new EscalationResult(
                    responders.stream().map(CircleMember::name).toList(),
                    escalation.composeToResponders(protectedName, request.aboutMoney()),
                    escalation.composeConfirmation(responders.size()),
                    false,
                    "v0.1 composes these messages but does not send them. Push and SMS "
                            + "delivery is the v0.2 task."));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * The full scam pattern library.
     *
     * @return every known pattern
     */
    @GetMapping("/scam-patterns")
    public List<ScamPattern> scamPatterns() {
        return library.all();
    }

    /** Refusal body for a rejected request. */
    public record Refusal(String error, String message) { }

    /** Passphrase guidance. */
    public record Instructions(String agreement, String usage, String whyWeNeverStoreIt) { }

    /** Pause screen content. */
    public record PauseScreen(String headline, List<PauseChecklist.ChecklistItem> items) { }

    /** Result of a call check. */
    public record CallCheck(int score, String level, String headline, String action,
                            boolean standaloneRedFlag, List<ScamPattern> likelyPatterns,
                            String caveat) { }

    /** Circle setup status. */
    public record CircleStatus(String circleId, String name, String passphraseStatus,
                               String passphraseExplanation, boolean setupComplete,
                               List<String> outstandingSteps, int responderCount,
                               String usageRule) { }

    /** Escalation outcome. */
    public record EscalationResult(List<String> notified, String messageToResponders,
                                   String confirmationToUser, boolean actuallyDelivered,
                                   String deliveryNote) { }
}
