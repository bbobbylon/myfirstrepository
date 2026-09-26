-- RenewalGuard v0.2: storage and identity, in one migration.
--
-- v0.1 kept cases in a HashMap and took the owner from the request. Three consequences:
--
--   1. Every tracked case vanished on restart - in an app whose entire purpose is to
--      remember a deadline you might forget. An amnesiac reminder service is worse than
--      no reminder service, because the user stops keeping their own note.
--   2. `GET /api/cases/{caseId}/status` checked nothing at all. Anyone holding or guessing
--      a case id could read that it belongs to someone enrolled in Medicaid in a named
--      state, in a named eligibility category.
--   3. `POST /api/cases` took `userId` from the body, so a caller could file cases under
--      anyone's id - or quietly collect the ids of real users by trying.
--
-- Point 2 is the one that matters most, and it is worth being precise about why. This table
-- holds no SSN, no income and no household data (see below). What it does hold is the FACT
-- of enrolment, and for a benefits population that fact is itself the sensitive part: it
-- discloses disability status, pregnancy, or poverty to anyone who reads a row.

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

CREATE TABLE benefit_cases (
    id                  TEXT PRIMARY KEY,

    -- The whole point of this migration. A case belongs to an account, and every read
    -- resolves it from (id, user_id) rather than from the id alone.
    -- ON DELETE CASCADE: closing an account takes its cases with it. An orphaned case is a
    -- record that somebody is on Medicaid, kept by an app nobody can log into to delete it.
    user_id             TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    program             TEXT NOT NULL,

    -- Two letters, stored upper case. CHAR(2) would pad rather than reject, which hides a
    -- bad value instead of surfacing it.
    state_code          TEXT NOT NULL,

    category            TEXT NOT NULL,

    -- The deadline. NOT NULL because every projection in the app is measured from it, and
    -- BenefitCase's constructor already refuses to exist without one - this is the copy of
    -- that rule which no code path can bypass, including an import script nobody wrote yet.
    renewal_due_on      DATE NOT NULL,

    -- Nullable: the notice has not arrived yet.
    notice_received_on  DATE,

    -- Nullable: the address has never been confirmed with the agency. NULL is the honest
    -- representation and the app treats it as "needs checking", which is the cautious
    -- reading - an unconfirmed address is the single largest contributor to a notice going
    -- undelivered and a case closing for someone who never stopped qualifying.
    address_confirmed_on DATE,

    -- Days to respond as printed on the user's own notice, when they told us.
    --
    -- THIS COLUMN IS A BUG FIX. In v0.1 it lived in a ConcurrentHashMap field on the
    -- controller, beside the repository rather than in it. Three things were wrong with
    -- that: it was lost on restart while the case it described survived; a second replica
    -- had its own empty copy, so the same case answered differently depending on which
    -- instance you reached; and the case and its window could not be written atomically.
    -- It belongs on the row.
    response_window_days INT,

    CONSTRAINT benefit_cases_state_code_is_two_letters
        CHECK (state_code ~ '^[A-Z]{2}$'),

    -- A non-positive window would make documentsReadyBy project a deadline on or after the
    -- date it is meant to precede. Validation exists at the API edge too; this is the copy
    -- the database enforces.
    CONSTRAINT benefit_cases_response_window_is_positive
        CHECK (response_window_days IS NULL OR response_window_days > 0)

    -- NOTE WHAT IS NOT HERE: no SSN, no income, no household composition, no immigration
    -- status, no date of birth. RenewalGuard tracks WHEN a renewal is due and WHAT paperwork
    -- it needs; it never evaluates whether someone qualifies, so it has no business holding
    -- the data an eligibility determination would need. A breach here would fall on the
    -- population least able to absorb it, and the most secure data is the data you chose not
    -- to collect. These absences are the privacy design, not gaps to fill in later.
);

-- The index that serves the list endpoint. Also the one that makes the ownership check on
-- every read cheap, which matters: a security check nobody can afford gets removed.
CREATE INDEX idx_benefit_cases_user ON benefit_cases (user_id);

-- Serves the daily reminder sweep, which asks "whose renewal is near?" across everyone
-- rather than per user.
CREATE INDEX idx_benefit_cases_due ON benefit_cases (renewal_due_on);

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
