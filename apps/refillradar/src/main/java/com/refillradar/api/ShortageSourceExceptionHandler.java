package com.refillradar.api;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.refillradar.refill.RefillProjector;
import com.refillradar.shortage.ShortageFetchException;

/**
 * Turns a failed shortage fetch into an honest error response instead of a bare 500.
 *
 * <p>Added after a real development failure: with {@code shortage-source=openfda} behind an
 * egress proxy blocking {@code api.fda.gov}, the fetch threw and the API returned a bare
 * {@code 500}. Throwing was right - {@link ShortageFetchException} exists so a failed fetch
 * never renders as "no shortages found" - but a raw {@code 500} leaves a client guessing,
 * and a guessing client eventually guesses "probably fine".
 *
 * <p>So the body states what failed and what must <em>not</em> be concluded from it.
 * {@code 503} is the accurate status: a valid request we are temporarily unable to answer,
 * as opposed to client error ({@code 4xx}) or broken logic ({@code 500}).
 */
@RestControllerAdvice
public class ShortageSourceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ShortageSourceExceptionHandler.class);

    private final RefillProjector projector;

    /**
     * @param projector supplies the evaluation date for the error payload
     */
    public ShortageSourceExceptionHandler(RefillProjector projector) {
        this.projector = projector;
    }

    /**
     * Handles a failure to retrieve shortage data.
     *
     * @param exception the failure
     * @return {@code 503} with an explicit statement that no conclusion should be drawn
     */
    @ExceptionHandler(ShortageFetchException.class)
    public ResponseEntity<FetchFailure> handleFetchFailure(ShortageFetchException exception) {
        log.error("Shortage data unavailable - refusing to imply an all-clear", exception);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new FetchFailure(
                "shortage_data_unavailable",
                "We could not reach the FDA shortage database, so we do not know whether "
                        + "your medications are affected.",
                // Stated outright. Silence about a failure is how software accidentally
                // reassures people, and this application's entire premise is the opposite.
                "This is NOT an all-clear. Please check with your pharmacist if you are "
                        + "concerned about a refill.",
                projector.today()));
    }

    /**
     * Structured error body for a failed shortage fetch.
     *
     * @param error       stable machine-readable code for clients to branch on
     * @param message     what went wrong, in plain language
     * @param important   what the reader must not conclude from the failure
     * @param checkedAt   the date the attempt was made
     */
    public record FetchFailure(String error, String message, String important, LocalDate checkedAt) {
    }
}
