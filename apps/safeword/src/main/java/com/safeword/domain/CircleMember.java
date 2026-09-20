package com.safeword.domain;

/**
 * One person in a family circle.
 *
 * <p>Holds a name and a contact route, and nothing else. No date of birth, no address, no
 * financial data - SafeWord intervenes at the moment of a call and has no use for any of it.
 *
 * @param id        stable identifier
 * @param name      how the family refers to them
 * @param role      what they do in the circle
 * @param contact   phone number or push token used to reach them
 */
public record CircleMember(String id, String name, MemberRole role, String contact) {

    /**
     * Validates the fields escalation depends on.
     *
     * @throws IllegalArgumentException if a responder has no contact route, which would make
     *         them silently unreachable at the one moment they are needed
     */
    public CircleMember {
        if (role == MemberRole.RESPONDER && (contact == null || contact.isBlank())) {
            throw new IllegalArgumentException(
                    "Responder " + name + " has no contact route - they could never be reached");
        }
    }
}
