package com.refillradar.matching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.refillradar.domain.Medication;
import com.refillradar.domain.ShortageMatch;
import com.refillradar.domain.ShortageRecord;
import com.refillradar.domain.SupplyRisk;
import com.refillradar.refill.RefillProjector;

/**
 * The engine: intersects a user's medication list with the FDA shortage feed and ranks the
 * results by how soon that user actually runs out.
 *
 * <p>This is the "two guest lists" problem described on {@link DrugNameNormalizer} - this
 * class performs the comparison, the normaliser decides when two differently-spelled names
 * refer to the same drug.
 *
 * <h2>Design note: no database, no network, no clock of its own</h2>
 * Everything this class needs arrives as a method argument or a constructor dependency. It
 * never fetches anything. That makes the most important logic in the application testable
 * in milliseconds with no Docker, no Postgres and no internet - which matters especially
 * here, because the session this was written in could not reach {@code api.fda.gov} at all.
 * Code that is hard to test tends to be code that stays untested.
 */
@Service
public class ShortageMatcher {

    private static final Logger log = LoggerFactory.getLogger(ShortageMatcher.class);

    private final DrugNameNormalizer normalizer;
    private final RefillProjector projector;

    /**
     * Creates the matcher.
     *
     * @param normalizer resolves differing spellings of the same drug
     * @param projector  supplies run-out dates and urgency levels
     */
    public ShortageMatcher(DrugNameNormalizer normalizer, RefillProjector projector) {
        this.normalizer = normalizer;
        this.projector = projector;
    }

    /**
     * Finds every shortage record that plausibly affects any of the given medications.
     *
     * <p>Results are sorted most-urgent-first, so a caller rendering an alert can simply
     * take the head of the list. Records the FDA has marked resolved are skipped entirely.
     *
     * @param medications the user's medications; {@code null} is treated as empty
     * @param shortages   the current FDA feed; {@code null} is treated as empty
     * @return matches ordered by ascending days remaining, never {@code null}
     */
    public List<ShortageMatch> findMatches(Collection<Medication> medications,
                                           Collection<ShortageRecord> shortages) {
        if (medications == null || medications.isEmpty() || shortages == null || shortages.isEmpty()) {
            return List.of();
        }

        List<ShortageMatch> matches = new ArrayList<>();

        for (Medication medication : medications) {
            Set<String> medicationTokens = normalizer.normalize(medication.searchTerm());

            if (medicationTokens.isEmpty()) {
                // Nothing matchable survived normalisation. Logged rather than swallowed,
                // because this is exactly the silent-failure mode that makes the app look
                // like it is working while it quietly protects nobody.
                log.warn("Medication '{}' normalised to zero tokens and cannot be matched",
                        medication.displayName());
                continue;
            }

            for (ShortageRecord shortage : shortages) {
                if (!shortage.isPotentiallyActive()) {
                    continue;
                }
                String overlap = firstOverlap(medicationTokens, shortage);
                if (overlap != null) {
                    long daysRemaining = projector.daysRemaining(medication);
                    matches.add(new ShortageMatch(
                            medication,
                            shortage,
                            medication.projectedRunOutDate(),
                            daysRemaining,
                            SupplyRisk.fromDaysRemaining(daysRemaining),
                            overlap));
                }
            }
        }

        // Soonest run-out first: that is the order a worried person wants to read in.
        matches.sort(Comparator.comparingLong(ShortageMatch::daysRemaining));
        return matches;
    }

    /**
     * Finds only the matches urgent enough to notify a user about.
     *
     * @param medications the user's medications
     * @param shortages   the current FDA feed
     * @return the subset of {@link #findMatches} where {@link ShortageMatch#warrantsAlert()}
     *         holds, preserving urgency order
     */
    public List<ShortageMatch> findAlertableMatches(Collection<Medication> medications,
                                                    Collection<ShortageRecord> shortages) {
        return findMatches(medications, shortages).stream()
                .filter(ShortageMatch::warrantsAlert)
                .toList();
    }

    /**
     * Returns the first normalised token shared by a medication and a shortage record.
     *
     * <p>Both the generic and the proprietary name of the record are considered, because
     * the FDA populates them inconsistently and a user may have entered either.
     *
     * <p>Comparison is on whole normalised tokens, never substrings. Substring matching
     * would be a serious bug here: {@code "amphetamine"} is a substring of
     * {@code "methamphetamine"}, and {@code "codeine"} of {@code "hydrocodone"}. Those are
     * different drugs, and a false positive that tells someone their epilepsy medication is
     * short when it is not is a real harm, not a cosmetic one.
     *
     * @param medicationTokens normalised tokens from the user's medication
     * @param shortage         the FDA record to compare against
     * @return the shared token, or {@code null} if there is no overlap
     */
    private String firstOverlap(Set<String> medicationTokens, ShortageRecord shortage) {
        Set<String> shortageTokens = new HashSet<>(normalizer.normalize(shortage.genericName()));
        shortageTokens.addAll(normalizer.normalize(shortage.proprietaryName()));

        for (String token : medicationTokens) {
            if (shortageTokens.contains(token)) {
                return token;
            }
        }
        return null;
    }
}
