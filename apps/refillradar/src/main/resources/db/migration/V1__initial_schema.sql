-- RefillRadar v0.3 initial schema.
--
-- Flyway owns this file and every one after it. Hibernate runs with ddl-auto=validate, so
-- it checks the entities against what is here and refuses to start on a mismatch - it never
-- alters the schema itself. An ORM that quietly rewrites production tables at startup is how
-- a column, and the data in it, disappears without anyone approving a diff.
--
-- Naming is snake_case to match PostgreSQL convention; Spring's default naming strategy maps
-- lastFilledOn -> last_filled_on, so the entities carry no @Column overrides.

CREATE TABLE medications (
    id             TEXT    PRIMARY KEY,
    user_id        TEXT    NOT NULL,
    display_name   TEXT    NOT NULL,
    search_term    TEXT    NOT NULL,
    last_filled_on DATE    NOT NULL,
    days_supply    INTEGER NOT NULL,

    -- Mirrors the invariant in the Medication record's compact constructor. Defence in
    -- depth: a future code path that bypasses the constructor (a bulk import, a repair
    -- script) still cannot store a value that would produce a nonsensical run-out date.
    CONSTRAINT medications_days_supply_positive CHECK (days_supply > 0)
);

-- findByUserId is the hot path: every API request and every alerting pass filters on it.
CREATE INDEX idx_medications_user_id ON medications (user_id);

-- Contact details live in their own table rather than as columns on medications, for the
-- reason ContactRepository gives: a medication list is health data, an email address is not,
-- and a future retention or encryption policy needs to be able to treat them differently.
CREATE TABLE contacts (
    user_id TEXT PRIMARY KEY,
    email   TEXT NOT NULL
);

-- What we have already told each user, so the nightly job does not repeat itself. In v0.2
-- this was a ConcurrentHashMap, which meant every restart re-sent every alert - the exact
-- notification fatigue AlertLedger exists to prevent.
CREATE TABLE alert_records (
    alert_key TEXT        PRIMARY KEY,
    risk      TEXT        NOT NULL,

    -- TIMESTAMPTZ, never TIMESTAMP. The repeat window is measured between two instants, and
    -- a timestamp with no zone is ambiguous for one hour twice a year.
    sent_at   TIMESTAMPTZ NOT NULL
);
