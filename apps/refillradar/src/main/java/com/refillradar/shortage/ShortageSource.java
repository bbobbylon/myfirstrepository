package com.refillradar.shortage;

import java.util.List;

import com.refillradar.domain.ShortageRecord;

/**
 * Supplies the current set of FDA drug-shortage records.
 *
 * <h2>Why this is an interface with two implementations</h2>
 * This is the seam that keeps the application testable and buildable offline, and it is
 * worth understanding as a general technique rather than a workaround.
 *
 * <ul>
 *   <li>{@link OpenFdaShortageSource} calls the live openFDA API. Used in production.</li>
 *   <li>{@link FixtureShortageSource} replays a recorded JSON response from the classpath.
 *       Used in tests and local development.</li>
 * </ul>
 *
 * <p><b>The concrete reason this exists here.</b> The Claude Code web session this project
 * was written in has an egress policy that permits only {@code github.com} and package
 * registries; {@code api.fda.gov} returns {@code 403} at the proxy. Without this seam,
 * nothing could have been built or verified at all.
 *
 * <p><b>The general reason it should stay.</b> Even with unrestricted network access you
 * want this. A test suite that calls a third-party API is slow, is rate-limited, and goes
 * red when someone else's server has a bad day. A build that fails for reasons unrelated to
 * your code is a build people learn to ignore - and a CI signal nobody trusts is worse than
 * no CI at all.
 *
 * <p>The analogy: a flight simulator. You do not teach emergency landings by crashing real
 * aircraft. You want to exercise the interesting cases - a malformed record, an unknown
 * status, an empty feed - on demand, and you cannot ask the FDA to produce those to order.
 */
public interface ShortageSource {

    /**
     * Fetches the current shortage records.
     *
     * @return the records, never {@code null}; empty if the source has nothing to report
     * @throws ShortageFetchException if the records could not be retrieved or parsed
     */
    List<ShortageRecord> fetchCurrentShortages();

    /**
     * A short label identifying where this data came from.
     *
     * <p>Shown to users alongside every alert. A tool that tells someone their epilepsy
     * medication may be unavailable owes them the provenance of that claim, and a build
     * accidentally running on fixtures must be visibly distinguishable from one running on
     * live FDA data.
     *
     * @return a human-readable source description, never {@code null}
     */
    String describeSource();
}
