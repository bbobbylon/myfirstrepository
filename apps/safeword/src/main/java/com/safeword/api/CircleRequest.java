package com.safeword.api;

import java.time.LocalDate;
import java.util.List;

import com.safeword.domain.MemberRole;

import jakarta.validation.constraints.NotBlank;

/**
 * Incoming payload for creating a family circle.
 *
 * <p>The {@code passphrase} field exists <b>only to be refused</b>. Leaving it out entirely
 * would mean a client that sent one had it silently dropped, and silent behaviour is exactly
 * what you do not want around a secret. Present and actively rejected is clearer than absent:
 * the caller gets an explanation, and the refusal is testable.
 *
 * @param name               what the family calls this circle
 * @param passphraseAgreedOn when they agreed their passphrase in person, or {@code null}
 * @param passphrase         MUST be empty. Sending a value is refused with an explanation.
 * @param members            the people in the circle
 */
public record CircleRequest(
        @NotBlank(message = "name is required")
        String name,

        LocalDate passphraseAgreedOn,

        String passphrase,

        List<MemberRequest> members) {

    /**
     * One circle member as supplied by a client.
     *
     * @param name    their name
     * @param role    their role; defaults to responder
     * @param contact phone number or push token
     */
    public record MemberRequest(
            @NotBlank(message = "member name is required")
            String name,
            MemberRole role,
            String contact) {
    }
}
