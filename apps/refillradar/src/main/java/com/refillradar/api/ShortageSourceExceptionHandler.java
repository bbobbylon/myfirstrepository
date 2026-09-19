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
 * <h2>Why this class earns its place</h2>
 * Added after a real failure during development. With
 * {@code refillradar.shortage-source=openfda} set in an environment whose egress proxy
 * blocks {@code api.fda.gov}, the fetch threw and the API returned an unhandled
 * {@code 500} with a stack trace.
 *
 * <p>The behaviour underneath was correct and is worth keeping: the fetch failed
 * <em>loudly</em> rather than returning an empty list. An empty list would have rendered as
 * "no shortages found", which is the single worst thing this application can say when it
 * actually knows nothing. That distinction is the whole reason
 * {@link ShortageFetchException} exists.
 *
 * <p>But a raw {@code 500} still leaves a client guessing, and a client that guesses will
 * eventually guess "probably fine". This handler removes the ambiguity by saying, in the
 * response body, exactly what failed and what the reader should not conclude from it.
 *
 * <p>{@code 503 Service Unavailable} is the accurate status: the request was valid and we
 * are temporarily unable to answer it, which is different from the client having erred
 * ({@code 4xx}) or our own logic being broken ({@code 500}).
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
