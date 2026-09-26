package com.safeword.api;

/**
 * A request for help from the circle.
 *
 * @param aboutMoney whether the call involves money or a transfer, which changes the wording
 *                   sent to responders
 */
public record EscalateRequest(boolean aboutMoney) {
}
