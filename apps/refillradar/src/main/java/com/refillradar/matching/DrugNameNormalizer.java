package com.refillradar.matching;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Reduces messy human and regulatory drug names to comparable ingredient tokens.
 *
 * <p>The hard part of the application. A user types {@code "Adderall XR 10mg"}; the FDA
 * publishes {@code "AMPHETAMINE ASPARTATE; AMPHETAMINE SULFATE; ..."}. Same medicine, zero
 * shared words. Get this wrong and the app fails <em>silently</em> - no alerts, and silence
 * looks like good news. Hence the heaviest test coverage in the project.
 *
 * <p>Normalisation strips strengths, dosage forms, punctuation and casing, and splits
 * combination products into separate tokens.
 *
 * <p><b>Known limitation:</b> the brand-to-generic map below is a hand-seeded stopgap. The
 * correct solution is RxNorm (NIH/NLM), deferred to v0.3 because it needs network access
 * and caching. Until then unknown brands produce false negatives - the dangerous direction -
 * which {@link #recognisesBrand} surfaces to users rather than hiding.
 */
@Component
public class DrugNameNormalizer {

    /**
     * Strength expressions: a number, optional decimal, optional unit.
     * Matches {@code 10mg}, {@code 0.5 mcg}, {@code 100 units/ml}, {@code 2.5%}.
     */
    private static final Pattern STRENGTH =
            Pattern.compile("\\b\\d+(\\.\\d+)?\\s*(mg|mcg|ug|g|ml|l|%|units?|iu)?(/\\s*\\w+)?\\b",
                    Pattern.CASE_INSENSITIVE);

    /** Anything that is not a letter, digit or space becomes a separator. */
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9 ]");

    /** Collapses runs of whitespace introduced by the strip steps above. */
    private static final Pattern MULTISPACE = Pattern.compile("\\s+");

    /**
     * Words that describe how a drug is delivered rather than what it is.
     *
     * <p>These carry no identifying information for matching. "Adderall XR" and "Adderall"
     * are the same active ingredient; if we kept {@code xr} as a token, a user on the
     * extended-release form would not match an FDA record about the immediate-release form
     * of the same molecule - and they very likely still care about that shortage.
     */
    private static final Set<String> NOISE_WORDS = Set.of(
            "tablet", "tablets", "tab", "tabs",
            "capsule", "capsules", "cap", "caps",
            "injection", "injectable", "solution", "suspension", "syrup", "elixir",
            "cream", "ointment", "gel", "patch", "spray", "inhaler", "inhalation",
            "oral", "topical", "intravenous", "iv", "im", "subcutaneous",
            "er", "xr", "sr", "cr", "xl", "la", "dr", "odt",
            "extended", "release", "immediate", "delayed", "controlled", "sustained",
            "hcl", "hydrochloride", "sulfate", "sulphate", "aspartate", "succinate",
            "sodium", "potassium", "calcium", "maleate", "tartrate", "besylate", "citrate",
            "usp", "generic", "brand");

    /**
     * Hand-seeded brand to active-ingredient mappings.
     *
     * <p>Keys are normalised brand names, values are the ingredient token to match on.
     * Intentionally short: this is a stopgap until RxNorm lands, and a small honest map is
     * better than a large guessed one. Every entry here should be verifiable against the
     * product's labelling.
     */
    private static final Map<String, String> BRAND_TO_INGREDIENT = Map.ofEntries(
            Map.entry("adderall", "amphetamine"),
            Map.entry("ritalin", "methylphenidate"),
            Map.entry("concerta", "methylphenidate"),
            Map.entry("vyvanse", "lisdexamfetamine"),
            Map.entry("wellbutrin", "bupropion"),
            Map.entry("zoloft", "sertraline"),
            Map.entry("prozac", "fluoxetine"),
            Map.entry("lexapro", "escitalopram"),
            Map.entry("ozempic", "semaglutide"),
            Map.entry("wegovy", "semaglutide"),
            Map.entry("mounjaro", "tirzepatide"),
            Map.entry("ventolin", "albuterol"),
            Map.entry("epipen", "epinephrine"),
            Map.entry("lasix", "furosemide"),
            Map.entry("synthroid", "levothyroxine"),
            Map.entry("keppra", "levetiracetam"),
            Map.entry("dilantin", "phenytoin"),
            Map.entry("tegretol", "carbamazepine"),
            Map.entry("lamictal", "lamotrigine"),
            Map.entry("amoxil", "amoxicillin"));

    /**
     * Normalises a raw drug name into the set of ingredient tokens it represents.
     *
     * <p>A combination product yields several tokens - {@code "amlodipine; benazepril"}
     * becomes {@code [amlodipine, benazepril]} - so that a shortage of either component
     * matches. A brand name known to {@link #BRAND_TO_INGREDIENT} yields both the brand and
     * its ingredient, so matching succeeds whichever form the other side uses.
     *
     * @param rawName the name as typed by a user or published by the FDA; may be
     *                {@code null} or blank
     * @return normalised ingredient tokens, never {@code null}; empty if nothing survived
     *         normalisation
     */
    public Set<String> normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return Set.of();
        }

        String working = rawName.toLowerCase(Locale.ROOT);

        // Split combination products first, while their separators are still present.
        // Done before punctuation stripping, otherwise ";" and "/" vanish and two
        // ingredients silently fuse into one meaningless token.
        String[] parts = working.split("[;/+]|\\band\\b|\\bwith\\b");

        Set<String> tokens = new LinkedHashSet<>();
        for (String part : parts) {
            String cleaned = STRENGTH.matcher(part).replaceAll(" ");
            cleaned = NON_ALPHANUMERIC.matcher(cleaned).replaceAll(" ");
            cleaned = MULTISPACE.matcher(cleaned).replaceAll(" ").trim();

            if (cleaned.isEmpty()) {
                continue;
            }

            // Drop delivery-and-salt noise, keeping only identifying words.
            String meaningful = Arrays.stream(cleaned.split(" "))
                    .filter(word -> !NOISE_WORDS.contains(word))
                    .filter(word -> word.length() > 2)   // drops stray letters and "mg" remnants
                    .collect(Collectors.joining(" "))
                    .trim();

            if (meaningful.isEmpty()) {
                continue;
            }

            tokens.add(meaningful);

            // A known brand contributes its generic ingredient as well, so that a user's
            // "Adderall" can meet the FDA's "amphetamine".
            for (String word : meaningful.split(" ")) {
                String ingredient = BRAND_TO_INGREDIENT.get(word);
                if (ingredient != null) {
                    tokens.add(ingredient);
                }
                // Multi-word remnants such as "amphetamine aspartate" have already had the
                // salt stripped, so index the individual words too. This is what lets
                // "amphetamine" match a compound FDA generic name.
                if (word.length() > 3) {
                    tokens.add(word);
                }
            }
        }
        return tokens;
    }

    /**
     * Whether this normaliser recognises a name as a brand it can map to an ingredient.
     *
     * <p>Used to tell a user "we don't recognise this brand name, so please also add the
     * generic name" - turning a silent false negative into a visible, fixable prompt.
     *
     * @param rawName the name to test
     * @return {@code true} if any word maps to a known ingredient
     */
    public boolean recognisesBrand(String rawName) {
        if (rawName == null) {
            return false;
        }
        String cleaned = NON_ALPHANUMERIC
                .matcher(rawName.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return Arrays.stream(cleaned.split("\\s+"))
                .anyMatch(BRAND_TO_INGREDIENT::containsKey);
    }
}
