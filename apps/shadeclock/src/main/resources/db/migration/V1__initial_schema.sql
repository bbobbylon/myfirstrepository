-- ShadeClock v0.2: storage and identity, in one migration.
--
-- v0.1 kept crews in a HashMap and had no authentication at all. The IDOR on
-- /api/crews/{crewId}/schedule was the lesser problem. The larger one needed no id:
--
--     GET /api/crews          ->  crews.findAll()
--
-- One unauthenticated request returned EVERY crew in the system: every worker's name, the
-- dates that reveal who has been off for a week or more, and the GPS coordinates of every
-- work site. Not a direct object reference to guess - an open door.
--
-- Why that data is worth protecting, since "a construction roster" can sound mundane:
--
--   * It names people, and pairs each name with a site location.
--   * lastAbsenceEndedOn marks the end of a break of a week or more. Absence of that length,
--     attached to a named employee, is health-adjacent by inference even though this app
--     never asks why somebody was away.
--   * The population is disproportionately migrant and hourly. A roster naming who works
--     where is exactly the artefact that should not be enumerable by a stranger.
--
-- Persistence and identity arrive together because neither is much use alone: accounts that
-- vanish on restart are not accounts, and durable crews nobody owns are still listable.

CREATE TABLE users (
    id            TEXT        PRIMARY KEY,

    -- A unique index on lower(username) rather than CITEXT, which needs an extension the
    -- deploying role may not be allowed to create.
    username      TEXT        NOT NULL,

    -- A hash, never a password, in Spring's self-describing form: "{bcrypt}$2a$10$...".
    -- The algorithm travels with the hash, so moving to argon2id later needs no reset.
    password_hash TEXT        NOT NULL,

    role          TEXT        NOT NULL DEFAULT 'USER',
    created_on    TIMESTAMPTZ NOT NULL,

    CONSTRAINT users_role_known CHECK (role IN ('USER', 'ADMIN'))
);

CREATE UNIQUE INDEX idx_users_username_lower ON users (lower(username));

CREATE TABLE crews (
    id               TEXT PRIMARY KEY,

    -- The whole point of this migration. A crew belongs to the supervisor's account, and
    -- every read resolves it from (id, owner_account_id) rather than from the id alone.
    -- ON DELETE CASCADE: closing an account takes its crews with it, because a roster nobody
    -- owns is a list of named workers that no one can log in to delete.
    owner_account_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    name             TEXT NOT NULL,
    site_label       TEXT NOT NULL,

    -- DOUBLE PRECISION, not NUMERIC: these feed a forecast lookup, not money. The forecast
    -- grid is coarser than the rounding error either way.
    latitude         DOUBLE PRECISION NOT NULL,
    longitude        DOUBLE PRECISION NOT NULL,

    -- Ruleset key, e.g. 'CA'. Deliberately NOT a foreign key to a jurisdictions table:
    -- the rulesets live in code (RulesetRegistry) because they encode legal thresholds that
    -- must be reviewed in a diff, not edited in a row.
    jurisdiction     TEXT NOT NULL,

    -- Latitude and longitude must be real coordinates. A swapped pair or a stray zero would
    -- silently fetch the weather for the wrong place, and a heat plan built from the wrong
    -- forecast is worse than no plan: it is confident and wrong.
    CONSTRAINT crews_latitude_in_range  CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT crews_longitude_in_range CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_crews_owner ON crews (owner_account_id);

CREATE TABLE crew_workers (
    id                   TEXT PRIMARY KEY,
    crew_id              TEXT NOT NULL REFERENCES crews (id) ON DELETE CASCADE,
    name                 TEXT NOT NULL,

    -- The first day of the current run of work in heat. NOT NULL because every
    -- acclimatisation calculation is measured from it, and Worker's constructor already
    -- refuses to exist without one - this is the copy of that rule no code path can bypass.
    heat_work_started_on DATE NOT NULL,

    -- The day a break of a week or more ended, or NULL if there has not been one. Nullable
    -- is the honest representation: "no absence recorded" is not the same as "worked every
    -- day", and the code treats a return from absence as restarting the adaptation clock.
    last_absence_ended_on DATE
);

CREATE INDEX idx_crew_workers_crew ON crew_workers (crew_id);

-- Failed login and registration attempts, counted in windows. Shared with the other apps
-- via libs/common-auth; the reasoning lives in LoginThrottle and RefillRadar's README.
CREATE TABLE login_attempts (
    id           TEXT        PRIMARY KEY,
    attempt_key  TEXT        NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL
);

-- Deliberately no foreign key to users: attempts against usernames that do not exist are
-- counted identically, or the 429 would answer the question login refuses to answer.
CREATE INDEX idx_login_attempts_key_time ON login_attempts (attempt_key, attempted_at);
CREATE INDEX idx_login_attempts_time ON login_attempts (attempted_at);

-- Spring Session's own schema, copied verbatim from
-- org/springframework/session/jdbc/schema-postgresql.sql in spring-session-jdbc 3.5.7,
-- which is the version this build actually resolves (checked with dependency:list, not
-- assumed - and the file is byte-identical in 3.4.3, so this is not version-fragile).
-- Checked in rather than left to spring.session.jdbc.initialize-schema, because Flyway owns
-- the schema here and two things creating the same tables is how they diverge.
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);
