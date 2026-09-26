package com.renewalguard.checklist;

/**
 * One item on a renewal document checklist.
 *
 * @param label       what to find, in the words a person would use
 * @param whyNeeded   what the agency uses it for
 * @param whereToFind a concrete hint about where it usually lives
 * @param oftenMissed whether this is a commonly forgotten item worth highlighting
 */
public record RequiredDocument(
        String label,
        String whyNeeded,
        String whereToFind,
        boolean oftenMissed) {
}
