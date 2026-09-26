-- RefillRadar v0.4: identity.
--
-- Until now `user_id` was whatever string the client put in the request. Anyone could read,
-- add to or delete anyone's medication list by guessing or supplying their id. This
-- migration introduces real accounts and makes user_id a foreign key to one of them.

CREATE TABLE users (
    id            TEXT        PRIMARY KEY,

    -- CITEXT would be the tidier way to get case-insensitive uniqueness, but it needs an
    -- extension the deploying role may not be able to CREATE. A unique index on lower()
    -- needs no privileges and cannot be bypassed by a code path that forgets to downcase.
    username      TEXT        NOT NULL,

    -- A hash, never a password, stored in Spring's self-describing form: "{bcrypt}$2a$10$...".
    -- The algorithm travels WITH the hash, so switching to argon2id later means new hashes
    -- use it and old ones still verify - no migration, no forced password reset.
    password_hash TEXT        NOT NULL,

    role          TEXT        NOT NULL DEFAULT 'USER',
    created_on    TIMESTAMPTZ NOT NULL,

    CONSTRAINT users_role_known CHECK (role IN ('USER', 'ADMIN'))
);

CREATE UNIQUE INDEX idx_users_username_lower ON users (lower(username));

-- Pre-v0.4 rows are DELETED here, deliberately.
--
-- Their user_id was an unauthenticated free-text string, not an identity - there is no
-- account they could be attributed to, and inventing one would be worse than dropping them.
-- This is destructive and it is called out in the README. It is safe only because no
-- deployment of this application has ever held real data; on a system that had, this step
-- would be a reconciliation exercise, not a DELETE.
DELETE FROM medications;
DELETE FROM contacts;
DELETE FROM alert_records;

-- Now the ids mean something, so the database can enforce that they refer to a real account.
-- ON DELETE CASCADE: closing an account takes its medication list with it. For health data
-- that is the right default - the alternative is orphaned rows nobody can reach or erase.
ALTER TABLE medications
    ADD CONSTRAINT medications_user_fk
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE contacts
    ADD CONSTRAINT contacts_user_fk
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- Spring Session's own schema, copied verbatim from
-- org/springframework/session/jdbc/schema-postgresql.sql in spring-session-jdbc 3.4.3.
-- Checked in rather than left to spring.session.jdbc.initialize-schema, because Flyway owns
-- the schema in this application and two things creating tables is how they diverge.
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
