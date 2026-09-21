package com.refillradar.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.refillradar.domain.Medication;
import com.refillradar.domain.ShortageMatch;
import com.refillradar.domain.ShortageRecord;
import com.refillradar.matching.DrugNameNormalizer;
import com.refillradar.matching.ShortageMatcher;
import com.refillradar.store.ContactRepository;
import com.refillradar.sync.ShortageCache;
import com.refillradar.sync.ShortageSyncJob;
import com.refillradar.sync.SyncOutcome;
import com.refillradar.refill.RefillProjector;
import com.refillradar.shortage.ShortageSource;
import com.refillradar.store.MedicationRepository;

import jakarta.validation.Valid;

/**
 * HTTP API for managing a medication list and running a supply check.
 *
 * <p>Deliberately thin. Controllers should translate between HTTP and the domain and do
 * nothing else - all real logic lives in {@link ShortageMatcher} and friends, which is what
 * makes that logic testable without starting a web server.
 *
 * <p><b>No authentication in v0.1.</b> {@code userId} is passed in the clear and trusted.
 * That is acceptable only for a local prototype and must be fixed before this is exposed to
 * anyone: a medication list is sensitive health information, and right now any caller can
 * read any user's list by guessing an id. This is called out here, in the code, rather than
 * left as an unwritten assumption.
 */
@RestController
@RequestMapping("/api")
public class RefillRadarController {

    private final MedicationRepository repository;
    private final ShortageMatcher matcher;
    private final ShortageSource shortageSource;
    private final RefillProjector projector;
    private final DrugNameNormalizer normalizer;
    private final ShortageSyncJob syncJob;
    private final ShortageCache cache;
    private final ContactRepository contacts;

    /**
     * @param repository     medication storage
     * @param matcher        the matching engine
     * @param shortageSource where FDA data comes from
     * @param projector      supplies the evaluation date
     * @param normalizer     used to warn about unrecognised brand names
     * @param syncJob        the nightly sync, exposed for manual triggering
     * @param cache          last-known-good feed, for the freshness note
     * @param contacts       where alerts can be sent
     */
    public RefillRadarController(MedicationRepository repository,
                                 ShortageMatcher matcher,
                                 ShortageSource shortageSource,
                                 RefillProjector projector,
                                 DrugNameNormalizer normalizer,
                                 ShortageSyncJob syncJob,
                                 ShortageCache cache,
                                 ContactRepository contacts) {
        this.repository = repository;
        this.matcher = matcher;
        this.shortageSource = shortageSource;
        this.projector = projector;
        this.normalizer = normalizer;
        this.syncJob = syncJob;
        this.cache = cache;
        this.contacts = contacts;
    }

    /**
     * Registers a medication.
     *
     * @param request the medication to add; validated before this method runs
     * @return {@code 201 Created} with the stored medication
     */
    @PostMapping("/medications")
    public ResponseEntity<Medication> addMedication(@Valid @RequestBody MedicationRequest request) {
        Medication medication = new Medication(
                UUID.randomUUID().toString(),
                request.userId(),
                request.displayName(),
                request.searchTerm(),
                request.lastFilledOn(),
                request.daysSupply());

        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(medication));
    }

    /**
     * Lists a user's medications.
     *
     * @param userId the owner
     * @return the user's medications, possibly empty
     */
    @GetMapping("/users/{userId}/medications")
    public List<Medication> listMedications(@PathVariable String userId) {
        return repository.findByUserId(userId);
    }

    /**
     * Removes a medication.
     *
     * @param id the medication id
     * @return {@code 204 No Content} if removed, {@code 404 Not Found} otherwise
     */
    @DeleteMapping("/medications/{id}")
    public ResponseEntity<Void> deleteMedication(@PathVariable String id) {
        return repository.deleteById(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Runs a supply check for one user: fetch the feed, match, rank, summarise.
     *
     * <p>Returns every match rather than only alertable ones, so a user opening the app on
     * purpose sees the full picture. The stricter {@code warrantsAlert} filter governs what
     * we <em>push</em> to them unprompted - asking is different from being interrupted.
     *
     * @param userId the user to check
     * @return the check result, including provenance and counts even when nothing matched
     */
    @GetMapping("/users/{userId}/supply-check")
    public SupplyCheckResponse checkSupply(@PathVariable String userId) {
        List<Medication> medications = repository.findByUserId(userId);
        List<ShortageRecord> shortages = shortageSource.fetchCurrentShortages();
        List<ShortageMatch> matches = matcher.findMatches(medications, shortages);

        List<SupplyCheckResponse.MatchSummary> summaries = matches.stream()
                .map(match -> new SupplyCheckResponse.MatchSummary(
                        match.medication().displayName(),
                        match.shortage().displayName(),
                        match.matchedOn(),
                        match.runOutDate(),
                        match.daysRemaining(),
                        match.risk().name(),
                        match.risk().recommendedAction(),
                        match.shortage().shortageReason(),
                        match.hasUncertainStatus()))
                .toList();

        return new SupplyCheckResponse(
                userId,
                projector.today(),
                shortageSource.describeSource(),
                medications.size(),
                shortages.size(),
                summaries);
    }

    /**
     * Reports whether a drug name will normalise to something matchable.
     *
     * <p>Exists because the failure mode this app most needs to avoid is silent: a user adds
     * "Dexilant", the normaliser has never heard of it, nothing ever matches, and the app
     * looks like it is working while protecting nobody. This endpoint lets the UI say
     * "we don't recognise that brand - please also enter the generic name" at the moment of
     * entry, turning an invisible failure into a fixable prompt.
     *
     * @param name the drug name to test
     * @return the normalised tokens and whether a known brand was recognised
     */
    @GetMapping("/debug/normalize")
    public NormalizationResult normalize(@org.springframework.web.bind.annotation.RequestParam String name) {
        return new NormalizationResult(
                name,
                List.copyOf(normalizer.normalize(name)),
                normalizer.recognisesBrand(name));
    }

    /**
     * Runs the nightly sync immediately.
     *
     * <p>Always give a scheduled job a manual trigger. Without one, every test cycle costs
     * you a day and you end up testing in production - which for this job would mean
     * emailing real people to find out whether the code works.
     *
     * @return what the sync did
     */
    @PostMapping("/admin/sync")
    public SyncOutcome triggerSync() {
        return syncJob.runSync();
    }

    /**
     * Reports the age of the cached shortage feed.
     *
     * <p>Separate from the supply check so monitoring can watch it. A sync that has been
     * quietly failing for a week is the failure mode most likely to go unnoticed, because
     * everything still <em>responds</em> - it just responds with old data.
     *
     * @return the cache's freshness
     */
    @GetMapping("/sync/status")
    public SyncStatus syncStatus() {
        return new SyncStatus(
                cache.current().isPresent(),
                cache.isStale(),
                cache.freshnessNote(),
                cache.current().map(snapshot -> snapshot.records().size()).orElse(0),
                cache.current().map(snapshot -> snapshot.fetchedAt().toString()).orElse(null));
    }

    /**
     * Records where a user's alerts should be sent.
     *
     * @param userId  the user
     * @param request the contact details
     * @return {@code 204 No Content}
     */
    @PostMapping("/users/{userId}/contact")
    public ResponseEntity<Void> setContact(@PathVariable String userId,
                                           @RequestBody ContactRequest request) {
        contacts.setEmail(userId, request.email());
        return ResponseEntity.noContent().build();
    }

    /**
     * Where a user's alerts would go.
     *
     * @param userId the user
     * @return reachability, so a UI can prompt for an address before promising alerts
     */
    @GetMapping("/users/{userId}/contact")
    public ContactStatus contactStatus(@PathVariable String userId) {
        return new ContactStatus(
                contacts.isReachable(userId),
                contacts.findEmail(userId).orElse(null),
                contacts.isReachable(userId) ? null
                        : "We have no way to reach you, so you will NOT receive alerts even "
                                + "if one of your medications goes into shortage.");
    }

    /**
     * Where alerts are sent.
     *
     * @param email the address
     */
    public record ContactRequest(String email) {
    }

    /**
     * Whether a user can actually be alerted.
     *
     * @param reachable whether a contact route exists
     * @param email     the address, if any
     * @param warning   what it means if there is none
     */
    public record ContactStatus(boolean reachable, String email, String warning) {
    }

    /**
     * The age and size of the cached shortage feed.
     *
     * @param hasData       whether any fetch has ever succeeded
     * @param stale         whether the data is old enough to warn about
     * @param note          plain-language freshness description
     * @param recordCount   how many records are cached
     * @param lastFetchedAt when the last successful fetch happened
     */
    public record SyncStatus(boolean hasData, boolean stale, String note,
                             int recordCount, String lastFetchedAt) {
    }

    /**
     * Diagnostic view of how a name was interpreted.
     *
     * @param input           the name as supplied
     * @param tokens          normalised tokens that will be used for matching
     * @param recognisedBrand whether a known brand-to-generic mapping applied
     */
    public record NormalizationResult(String input, List<String> tokens, boolean recognisedBrand) {
    }
}
