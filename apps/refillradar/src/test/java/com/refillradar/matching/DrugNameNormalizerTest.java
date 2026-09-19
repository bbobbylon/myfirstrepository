package com.refillradar.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link DrugNameNormalizer}, the highest-risk class in the application.
 *
 * <p>These tests carry more weight than their size suggests. If normalisation breaks, the
 * app does not crash - it simply stops matching anything and reports "no shortages found"
 * forever. That failure looks exactly like good news, which is why it has to be caught here
 * rather than in production.
 */
class DrugNameNormalizerTest {

    private final DrugNameNormalizer normalizer = new DrugNameNormalizer();

    @Nested
    @DisplayName("strips noise that carries no identifying information")
    class StripsNoise {

        @ParameterizedTest(name = "{0} -> contains \"adderall\"")
        @ValueSource(strings = {
                "Adderall",
                "adderall",
                "ADDERALL",
                "Adderall XR",
                "Adderall XR 10mg",
                "Adderall 10 mg tablets",
                "Adderall (extended release)"
        })
        @DisplayName("strength, dosage form and casing do not change the identity of a drug")
        void ignoresStrengthAndForm(String input) {
            assertThat(normalizer.normalize(input)).contains("adderall");
        }

        @Test
        @DisplayName("salt names are stripped so 'amphetamine aspartate' matches 'amphetamine'")
        void stripsSaltNames() {
            assertThat(normalizer.normalize("Amphetamine Aspartate")).contains("amphetamine");
        }
    }

    @Nested
    @DisplayName("bridges brand names to active ingredients")
    class BrandMapping {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "Adderall,      amphetamine",
                "Concerta,      methylphenidate",
                "Ozempic,       semaglutide",
                "Keppra,        levetiracetam",
                "Ventolin,      albuterol",
                "Synthroid,     levothyroxine"
        })
        @DisplayName("a known brand also yields its generic ingredient")
        void mapsBrandToIngredient(String brand, String expectedIngredient) {
            assertThat(normalizer.normalize(brand)).contains(expectedIngredient);
        }

        @Test
        @DisplayName("a user's brand name and the FDA's generic name meet in the middle")
        void brandAndGenericOverlap() {
            Set<String> userTyped = normalizer.normalize("Adderall XR 10mg");
            Set<String> fdaPublished = normalizer.normalize(
                    "AMPHETAMINE ASPARTATE; AMPHETAMINE SULFATE; DEXTROAMPHETAMINE SULFATE");

            // This single assertion is the entire product working. The two strings share no
            // words whatsoever, yet describe the same medicine.
            assertThat(userTyped).containsAnyElementsOf(fdaPublished);
        }

        @Test
        @DisplayName("reports when a brand is not in the mapping, so the UI can prompt")
        void reportsUnrecognisedBrands() {
            assertThat(normalizer.recognisesBrand("Adderall")).isTrue();
            assertThat(normalizer.recognisesBrand("Dexilant")).isFalse();
        }
    }

    @Nested
    @DisplayName("handles combination products")
    class Combinations {

        @Test
        @DisplayName("splits a multi-ingredient product into separately matchable tokens")
        void splitsOnSemicolon() {
            Set<String> tokens =
                    normalizer.normalize("AMLODIPINE BESYLATE; BENAZEPRIL HYDROCHLORIDE");

            assertThat(tokens).contains("amlodipine", "benazepril");
        }

        @Test
        @DisplayName("a shortage of either component is findable by that component's name")
        void eitherComponentMatches() {
            Set<String> combination =
                    normalizer.normalize("AMLODIPINE BESYLATE; BENAZEPRIL HYDROCHLORIDE");

            assertThat(combination).containsAnyElementsOf(normalizer.normalize("amlodipine"));
            assertThat(combination).containsAnyElementsOf(normalizer.normalize("benazepril"));
        }
    }

    @Nested
    @DisplayName("does NOT create false positives between different drugs")
    class NoFalsePositives {

        @Test
        @DisplayName("amphetamine must not match methamphetamine")
        void amphetamineIsNotMethamphetamine() {
            // Substring matching would wrongly link these. They are different drugs, and
            // falsely telling someone their medication is short is a real harm.
            assertThat(normalizer.normalize("amphetamine"))
                    .doesNotContainAnyElementsOf(normalizer.normalize("methamphetamine"));
        }

        @Test
        @DisplayName("codeine must not match hydrocodone")
        void codeineIsNotHydrocodone() {
            assertThat(normalizer.normalize("codeine"))
                    .doesNotContainAnyElementsOf(normalizer.normalize("hydrocodone"));
        }

        @Test
        @DisplayName("unrelated drugs share no tokens")
        void unrelatedDrugsDoNotOverlap() {
            assertThat(normalizer.normalize("Ozempic"))
                    .doesNotContainAnyElementsOf(normalizer.normalize("Keppra"));
        }
    }

    @Nested
    @DisplayName("degrades safely on bad input")
    class BadInput {

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "10mg", "500 mg tablets", "!!!"})
        @DisplayName("input with no identifying content yields no tokens rather than throwing")
        void returnsEmptyForMeaninglessInput(String input) {
            assertThat(normalizer.normalize(input)).isEmpty();
        }

        @Test
        @DisplayName("null is tolerated")
        void toleratesNull() {
            assertThat(normalizer.normalize(null)).isEmpty();
            assertThat(normalizer.recognisesBrand(null)).isFalse();
        }
    }
}
