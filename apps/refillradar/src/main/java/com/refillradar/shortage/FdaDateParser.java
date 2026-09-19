package com.refillradar.shortage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Parses the several date formats openFDA is known to emit, without ever throwing.
 *
 * <p>openFDA has historically returned dates in more than one shape across its endpoints -
 * compact {@code yyyyMMdd} and ISO {@code yyyy-MM-dd} both appear in the wild. Rather than
 * guess which one this endpoint uses and be wrong, we try each in turn.
 *
 * <p><b>Why unparseable dates return {@code null} rather than throwing.</b> A date is
 * supporting detail on a shortage record; the drug name and status are what actually drive
 * an alert. Aborting a nightly sync of 200+ records because one has a malformed
 * {@code update_date} would trade a cosmetic problem for a total outage. Degrade, do not
 * collapse.
 *
 * <p>Verified against openFDA's documented field list, but <b>not</b> against a live
 * response - the build environment could not reach {@code api.fda.gov}. Confirm the real
 * format on first live run and prune the unused patterns.
 */
public final class FdaDateParser {

    /** Tried in order; first success wins. */
    private static final List<DateTimeFormatter> FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("MM/dd/yyyy"));

    private FdaDateParser() {
        // Utility class - no instances.
    }

    /**
     * Attempts to parse an FDA date string.
     *
     * @param raw the date text; may be {@code null} or blank
     * @return the parsed date, or {@code null} if it was absent or in no recognised format
     */
    public static LocalDate parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        for (DateTimeFormatter format : FORMATS) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (DateTimeParseException ignored) {
                // Try the next pattern. Exhausting all of them yields null, by design.
            }
        }
        return null;
    }
}
