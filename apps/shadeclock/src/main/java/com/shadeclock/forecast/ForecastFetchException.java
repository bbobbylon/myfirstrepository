package com.shadeclock.forecast;

/**
 * Raised when a forecast could not be retrieved or understood.
 *
 * <p>Same reasoning as RefillRadar's {@code ShortageFetchException}: an empty forecast and a
 * failed fetch look identical to a naive caller, and rendering "no breaks needed" because
 * the weather service was down is exactly the false reassurance this application exists to
 * prevent. Failing loudly is the point.
 */
public class ForecastFetchException extends RuntimeException {

    /**
     * @param message what was being attempted
     * @param cause   the underlying failure
     */
    public ForecastFetchException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message what was being attempted
     */
    public ForecastFetchException(String message) {
        super(message);
    }
}
