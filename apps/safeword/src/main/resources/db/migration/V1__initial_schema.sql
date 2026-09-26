-- SafeWord v0.2: storage and identity, in one migration.
--
-- v0.1 kept circles in a HashMap and trusted the circle id in the URL. Two consequences,
-- and the second is the serious one:
--
--   1. Every circle vanished on restart.
--   2. Anyone holding a circle id could read that family's setup and raise an alarm to
--      them. v0.1 only COMPOSES escalation messages, so the damage was bounded - but the
--      moment delivery is wired up, that becomes a way to cry wolf at somebody else's
--      family until they learn to ignore the alert this app exists to send.
--
-- Persistence and identity arrive together because neither is much use alone: accounts that
-- disappear on restart are not accounts, and durable circles nobody owns are still readable
-- by anyone who can guess an id.

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

CREATE TABLE circles (
    id                   TEXT PRIMARY KEY,

    -- The whole point of this migration. A circle belongs to an account, and every read or
    -- escalation resolves the circle FROM the session rather than from the URL.
    -- ON DELETE CASCADE: closing an account takes its circle with it, because a circle
    -- nobody owns is a list of an older adult's family that nothing can reach or erase.
    owner_account_id     TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    name                 TEXT NOT NULL,

    -- The DATE a passphrase was agreed in person. Nullable: not agreed yet.
    passphrase_agreed_on DATE

    -- NOTE WHAT IS NOT HERE: there is no passphrase column, and there never will be.
    -- SafeWord knows THAT a family agreed a passphrase and WHEN; it must never know what it
    -- is. A stored passphrase is a stored answer to the question the family asks a caller
    -- to prove they are real - one breach and the protection inverts into a weapon. The
    -- absence of this column is not an omission to fill in later, it is the feature.
);

-- One circle per account for now. A person who belongs to two families - an adult child of
-- separately-living parents, say - is a real case this does not yet serve, and it is in the
-- README as a limitation rather than pretended away.
CREATE UNIQUE INDEX idx_circles_owner ON circles (owner_account_id);

CREATE TABLE circle_members (
    id        TEXT PRIMARY KEY,
    circle_id TEXT NOT NULL REFERENCES circles (id) ON DELETE CASCADE,
    name      TEXT NOT NULL,
    role      TEXT NOT NULL,

    -- Phone number or push token. Nullable only for the protected person, who is never
    -- contacted BY the escalation - they are the one raising it.
    contact   TEXT,

    CONSTRAINT circle_members_role_known
        CHECK (role IN ('PROTECTED_PERSON', 'RESPONDER')),

    -- A responder with no contact route is someone the family believes will be called and
    -- who cannot be. CircleMember's constructor rejects it too; this is the copy that no
    -- code path can forget, including a future import script nobody has written yet.
    CONSTRAINT circle_members_responder_is_reachable
        CHECK (role <> 'RESPONDER' OR (contact IS NOT NULL AND btrim(contact) <> ''))
);

CREATE INDEX idx_circle_members_circle ON circle_members (circle_id);

-- Failed login and registration attempts, counted in windows. Ported from RefillRadar v0.5;
-- the reasoning lives in that app's README and in LoginThrottle.
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
