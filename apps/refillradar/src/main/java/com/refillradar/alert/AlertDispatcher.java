package com.refillradar.alert;

/**
 * Sends a composed alert to a user.
 *
 * <p>An interface for the same reason as {@code ShortageSource}: the default implementation
 * needs no network, so the whole alerting path is testable offline, and a deployment that
 * has not configured mail fails towards something visible rather than towards silence.
 */
public interface AlertDispatcher {

    /**
     * Delivers an alert.
     *
     * @param userId  who to reach
     * @param subject the subject line
     * @param body    the message body
     * @return the outcome, never {@code null}
     */
    DispatchResult dispatch(String userId, String subject, String body);

    /**
     * A short label describing where alerts actually go.
     *
     * @return the description, never {@code null}
     */
    String describeChannel();

    /**
     * What happened to one dispatch attempt.
     *
     * @param delivered whether it actually reached a channel
     * @param detail    plain-language explanation, especially when it did not
     */
    record DispatchResult(boolean delivered, String detail) {
    }
}
