package com.commonauth.domain;

/**
 * What an account is allowed to do.
 *
 * <p>Two roles, because two is what the application actually has. Inventing a permission
 * framework before there are permissions to model produces an abstraction shaped by
 * guesses rather than by use.
 */
public enum AccountRole {

    /** An ordinary patient. Can only ever reach their own data. */
    USER,

    /** Can additionally trigger a sync and read operational status. */
    ADMIN
}
