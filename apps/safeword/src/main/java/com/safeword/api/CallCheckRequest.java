package com.safeword.api;

import java.util.Set;

import com.safeword.scam.PressureSignal;

/**
 * What the user reported about a call in progress.
 *
 * <p>Only tactics - things the caller <em>did</em>. No audio, no recording, no transcript.
 * SafeWord never analyses a voice, so it never needs to receive one.
 *
 * @param signals the tactics observed
 */
public record CallCheckRequest(Set<PressureSignal> signals) {
}
