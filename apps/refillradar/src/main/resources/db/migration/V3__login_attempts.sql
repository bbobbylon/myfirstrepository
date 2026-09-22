-- RefillRadar v0.5: rate limiting.
--
-- v0.4 added a password check. Nothing slowed down guessing at it, so the protection was
-- only ever as strong as the weakest password any user chose - and an attacker got
-- unlimited tries to find that user. This table is the memory that makes "too many tries"
-- a thing the application can notice.

CREATE TABLE login_attempts (
    id           TEXT        PRIMARY KEY,

    -- Who the attempt counts against: "user:<lowercased username>", "ip:<address>" or
    -- "reg:<address>". The policy owns this format; the table just counts rows.
    attempt_key  TEXT        NOT NULL,

    -- TIMESTAMPTZ, never TIMESTAMP. A plain timestamp is ambiguous for one hour twice a
    -- year, and a throttle whose window silently widens or narrows on those days is a
    -- throttle nobody can reason about.
    attempted_at TIMESTAMPTZ NOT NULL
);

-- Deliberately NO foreign key to users.
--
-- Failures against usernames that do not exist are recorded exactly like failures against
-- ones that do. If only real accounts were counted, the 429 would itself answer the
-- question login is careful never to answer: does this account exist here?
--
-- Nothing secret is stored - no password, no hash, no session id. A username that was
-- tried is not evidence of anything except that it was tried.

-- The throttle asks one question: how many rows with this key since T. A composite index in
-- that order answers it from the index alone; without it, every login attempt scans a table
-- that an attacker controls the size of.
CREATE INDEX idx_login_attempts_key_time ON login_attempts (attempt_key, attempted_at);

-- For the scheduled purge, which deletes by age across all keys.
CREATE INDEX idx_login_attempts_time ON login_attempts (attempted_at);
