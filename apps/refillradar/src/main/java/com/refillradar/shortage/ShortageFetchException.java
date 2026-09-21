package com.refillradar.shortage;

/**
 * Raised when shortage data could not be retrieved or understood.
 *
 * <p>Unchecked because there is no sensible local recovery. The nightly sync catches it at
 * the top level and leaves the previous known-good feed in place rather than treating a
 * failure as "no shortages exist" - an empty list and a failed fetch look identical to a
 * naive caller, and telling users "nothing is short" because the network was down is the
 * false reassurance this application exists to prevent.
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
