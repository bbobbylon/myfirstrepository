package com.safeword.domain;

/**
 * What a circle member does.
 *
 * <p>The buyer and the user are different people: the adult child installs SafeWord, the
 * parent is protected by it. Setup must therefore be completable by the child for the parent
 * in one sitting, possibly over the phone from another city.
 */
public enum MemberRole {

    /** The person SafeWord protects. Usually the older adult. */
    PROTECTED_PERSON,

    /** Someone who gets called when the protected person asks for help. */
    RESPONDER
}
