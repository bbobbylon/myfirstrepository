package com.refillradar.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.refillradar.domain.Medication;
import com.refillradar.domain.ShortageMatch;
import com.refillradar.domain.ShortageRecord;
import com.refillradar.matching.ShortageMatcher;
import com.refillradar.store.ContactRepository;
import com.refillradar.store.MedicationRepository;

/**
 * Evaluates every user against the current feed and sends the alerts that are warranted.
 *
 * <p>This is the class that finally closes RefillRadar's core loop. v0.1 could answer "is my
 * medication short?" when asked; it could not <em>tell</em> anyone. The product's entire
 * premise is lead time, and lead time requires the application to speak first.
 *
 * <p>The pipeline, in order, with a reason for each stage:
 * <ol>
 *   <li><b>Match</b> - who is affected at all</li>
 *   <li><b>Filter to alertable</b> - drop WATCH-level matches; messaging someone about a
 *       drug they refilled last week trains them to ignore us</li>
 *   <li><b>De-duplicate</b> - {@link AlertLedger}, so a months-long shortage does not
 *       produce a nightly email</li>
 *   <li><b>Group per user</b> - one message listing three medications, not three messages</li>
 *   <li><b>Dispatch</b> - and record honestly whether it actually went anywhere</li>
 * </ol>
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final MedicationRepository medications;
    private final ContactRepository contacts;
    private final ShortageMatcher matcher;
    private final AlertComposer composer;
    private final AlertLedger ledger;
    private final AlertDispatcher dispatcher;

    /**
     * @param medications the medication store
     * @param contacts    where users can be reached
     * @param matcher     the matching engine
     * @param composer    message copy
     * @param ledger      de-duplication
     * @param dispatcher  delivery
     */
    public AlertService(MedicationRepository medications,
                        ContactRepository contacts,
                        ShortageMatcher matcher,
                        AlertComposer composer,
                        AlertLedger ledger,
                        AlertDispatcher dispatcher) {
        this.medications = medications;
        this.contacts = contacts;
        this.matcher = matcher;
        this.composer = composer;
        this.ledger = ledger;
        this.dispatcher = dispatcher;
    }

    /**
     * Evaluates everyone and sends what is warranted.
     *
     * @param shortages   the current feed
     * @param sourceLabel provenance, quoted in every message
     * @return a summary of what was sent and suppressed
     */
    public AlertRun run(List<ShortageRecord> shortages, String sourceLabel) {
        Map<String, List<Medication>> byUser = medications.findAll().stream()
                .collect(Collectors.groupingBy(Medication::userId));

        int sent = 0;
        int suppressed = 0;
        List<String> unreachable = new ArrayList<>();

        for (Map.Entry<String, List<Medication>> entry : byUser.entrySet()) {
            String userId = entry.getKey();

            List<ShortageMatch> alertable =
                    matcher.findAlertableMatches(entry.getValue(), shortages);
            if (alertable.isEmpty()) {
                continue;
            }

            List<ShortageMatch> fresh = new ArrayList<>();
            for (ShortageMatch match : alertable) {
                if (ledger.shouldSend(match)) {
                    fresh.add(match);
                } else {
                    suppressed++;
                }
            }
            if (fresh.isEmpty()) {
                continue;
            }

            if (!contacts.isReachable(userId)) {
                // Recorded rather than swallowed. A user with alertable matches and no
                // contact route is a silent failure of the product's core promise, and
                // somebody should see it.
                unreachable.add(userId);
                log.warn("User {} has {} alertable match(es) but no contact route",
                        userId, fresh.size());
                continue;
            }

            AlertDispatcher.DispatchResult result = dispatcher.dispatch(
                    userId,
                    composer.composeSubject(fresh),
                    composer.composeBody(fresh, sourceLabel));

            // Only record once dispatch was attempted, so a crash mid-run does not leave a
            // user permanently de-duplicated out of an alert they never received.
            fresh.forEach(ledger::record);
            sent++;

            if (!result.delivered()) {
                log.info("Alert for {} was composed but not delivered: {}",
                        userId, result.detail());
            }
        }

        return new AlertRun(byUser.size(), sent, suppressed, unreachable,
                dispatcher.describeChannel());
    }

    /**
     * Summary of one alerting pass.
     *
     * @param usersEvaluated how many users were checked
     * @param alertsSent     how many messages were dispatched
     * @param suppressed     how many matches were suppressed as duplicates
     * @param unreachable    users who needed an alert but have no contact route
     * @param channel        where alerts go
     */
    public record AlertRun(int usersEvaluated, int alertsSent, int suppressed,
                           List<String> unreachable, String channel) {
    }
}
