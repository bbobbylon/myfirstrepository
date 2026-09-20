package com.safeword.domain;

/**
 * What a circle member does.
 *
 * <h2>The buyer and the user are different people</h2>
 * This split reflects the hardest problem in the product, which is distribution rather than
 * code. Older adults do not browse app stores for security software. The person who installs
 * SafeWord is typically the adult child; the person it protects is their parent. The whole
 * setup flow has to be completable <em>by</em> the child <em>for</em> the parent, in one
 * sitting, possibly over the phone from another city.
 */
public enum MemberRole {

    /** The person SafeWord protects. Usually the older adult. */
    PROTECTED_PERSON,

    /** Someone who gets called when the protected person asks for help. */
    RESPONDER
}
