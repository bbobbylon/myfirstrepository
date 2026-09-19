package com.refillradar.shortage;

/**
 * Raised when shortage data could not be retrieved or understood.
 *
 * <p>A checked-style failure deliberately surfaced as unchecked: there is no sensible local
 * recovery from "the FDA feed is unavailable", so callers should not be forced into empty
 * catch blocks. The nightly sync catches this at the top level, logs it, and - critically -
 * leaves the previous known-good data in place rather than treating a fetch failure as
 * "no shortages exist".
 *
 * <p>That distinction matters: an empty list and a failed fetch look identical to a naive
 * caller, but telling users "nothing is short" because the network was down is exactly the
 * false reassurance this application is supposed to prevent.
 */
public class ShortageFetchException extends RuntimeException {

    /**
     * @param message what was being attempted when the failure occurred
     * @param cause   the underlying failure
     */
    public ShortageFetchException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message what was being attempted when the failure occurred
     */
    public ShortageFetchException(String message) {
        super(message);
    }
}
