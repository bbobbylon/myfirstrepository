package com.safeword.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commonauth.web.AccountPrincipal;
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
 * <p><b>Two kinds of route, on purpose.</b> Anything under {@code /api/me} is one family's
 * circle and needs a session. The pause screen, the call check, the scam patterns and the
 * passphrase instructions stay open to anyone: they read nobody's data, and they are what a
 * person uses <em>while a suspicious call is happening</em>. A login prompt at that moment
 * would protect nothing and cost everything.
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
     * Creates the caller's family circle.
     *
     * <p>Rejects the request outright if it carries anything in the {@code passphrase} field.
     * That field exists in the request type only so a client sending one gets a clear
     * explanation instead of silence - and so the refusal is testable.
     *
     * @param request   the circle to create
     * @param principal the logged-in account, from the session
     * @return {@code 201 Created}; {@code 400} if a secret was sent; {@code 409} if this
     *         account already has a circle
     */
    @PostMapping("/me/circle")
    public ResponseEntity<?> createCircle(@Valid @RequestBody CircleRequest request,
                                          @AuthenticationPrincipal AccountPrincipal principal) {
        if (protocol.rejectsSuppliedSecret(request.passphrase())) {
            return ResponseEntity.badRequest()
                    .body(new Refusal("passphrase_not_accepted", protocol.secretRejectionMessage()));
        }

        // 409 rather than silently replacing. Overwriting would let a mistyped setup wipe a
        // family's responders without anyone being told, and the people this protects are
        // the least likely to notice a list got shorter.
        if (circles.existsForOwner(principal.accountId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new Refusal("circle_exists",
                    "You already have a circle. Send PUT /api/me/circle to change it."));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(circles.save(principal.accountId(), toCircle(request)));
    }

    /**
     * Replaces the caller's circle, creating it if there is none.
     *
     * <p>A whole-circle replace rather than per-member endpoints: removing a responder is
     * then a save without them, and there is no path where a client thinks it deleted
     * somebody and the server kept them.
     *
     * @param request   the desired circle
     * @param principal the logged-in account, from the session
     * @return the stored circle, or {@code 400} if a secret was sent
     */
    @PutMapping("/me/circle")
    public ResponseEntity<?> replaceCircle(@Valid @RequestBody CircleRequest request,
                                           @AuthenticationPrincipal AccountPrincipal principal) {
        if (protocol.rejectsSuppliedSecret(request.passphrase())) {
            return ResponseEntity.badRequest()
                    .body(new Refusal("passphrase_not_accepted", protocol.secretRejectionMessage()));
        }

        return ResponseEntity.ok(circles.save(principal.accountId(), toCircle(request)));
    }

    private FamilyCircle toCircle(CircleRequest request) {
        List<CircleMember> members = request.members() == null ? List.of()
                : request.members().stream()
                        .map(m -> new CircleMember(UUID.randomUUID().toString(), m.name(),
                                m.role() == null ? MemberRole.RESPONDER : m.role(), m.contact()))
                        .toList();

        // The id here is only used when the circle is new; a replace keeps the id the row
        // already has, so updating a circle never mints a new one.
        return new FamilyCircle(UUID.randomUUID().toString(), request.name(),
                request.passphraseAgreedOn(), members);
    }

    /**
     * Setup status for the caller's own circle.
     *
     * @param principal the logged-in account, from the session
     * @return the status, or {@code 404} if this account has not set up a circle
     */
    @GetMapping("/me/circle/status")
    public ResponseEntity<CircleStatus> circleStatus(
            @AuthenticationPrincipal AccountPrincipal principal) {
        LocalDate today = LocalDate.now(clock);
        return circles.findByOwner(principal.accountId())
                .map(circle -> ResponseEntity.ok(new CircleStatus(
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
     * @param request   what the call is about
     * @param principal the logged-in account, from the session
     * @return the escalation result, or {@code 404} if this account has no circle
     */
    @PostMapping("/me/circle/escalate")
    public ResponseEntity<EscalationResult> escalate(@RequestBody EscalateRequest request,
                                                     @AuthenticationPrincipal
                                                     AccountPrincipal principal) {
        return circles.findByOwner(principal.accountId()).map(circle -> {
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

    /**
     * Circle setup status.
     *
     * <p>No {@code circleId}. No endpoint accepts one any more, so returning it would be an
     * identifier with nothing to identify - and a standing invitation to add a route that
     * takes it back.
     */
    public record CircleStatus(String name, String passphraseStatus,
                               String passphraseExplanation, boolean setupComplete,
                               List<String> outstandingSteps, int responderCount,
                               String usageRule) { }

    /** Escalation outcome. */
    public record EscalationResult(List<String> notified, String messageToResponders,
                                   String confirmationToUser, boolean actuallyDelivered,
                                   String deliveryNote) { }
}
