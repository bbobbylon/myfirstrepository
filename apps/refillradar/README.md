# RefillRadar

**Personal early warning for drug shortages.** Watches the FDA shortage database against
*your* medication list and warns you weeks before a refill fails.

> **v0.5 — password guessing is now rate limited.** 153 tests, runs offline. Still not
> production ready: openFDA has never been verified live, and there is no password reset,
> no backup and no encryption at rest. See [Limitations](#limitations).
>
> **Not medical advice.** Never suggests alternative medicines. Talk to your pharmacist.

Research and competitive analysis: [`../../proposals/02-refillradar.md`](../../proposals/02-refillradar.md)

## Why

The FDA publishes its shortage list. So does ASHP. Both are public. But what happens to a
patient is: *turn up the day you run out → "we can't get it" → now you need an appointment,
an alternative, maybe prior authorisation — starting from zero doses.*

The information existed for weeks. It never reached the person whose refill date made it
urgent. **The data is public; the personalisation is missing.** The unique input is *your
refill date*.

ASHP counted **227 active US shortages** at end of Q2 2026 — a third consecutive quarterly
rise. CNS medications are the largest category, which includes anti-epileptics, where an
interrupted supply is a medical event.

## Quick start

JDK 21+, Maven 3.9+, PostgreSQL 14+. No network, no API key.

```bash
# One-time: create the database the defaults expect
createdb refillradar && createuser refillradar   # or see Deployment for the psql version

mvn test                # 153 tests, ~35s
mvn spring-boot:run     # :8080
```

No PostgreSQL to hand? `--spring.profiles.active=memory` runs everything in RAM. It is a
demo mode, not a deployment mode — **it loses all data on restart, including the alert
ledger, so a restarted instance re-sends every alert it has ever sent.**

Everything below needs a session, so register once and keep the cookie jar:

```bash
curl -c jar -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"robert","password":"a-memorable-passphrase"}'
curl -c jar -b jar -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"robert","password":"a-memorable-passphrase"}'

# State-changing requests also need the CSRF token from the XSRF-TOKEN cookie.
TOKEN=$(awk '/XSRF-TOKEN/ {print $7}' jar)
curl -b jar -H "X-XSRF-TOKEN: $TOKEN" \
  -X POST localhost:8080/api/medications -H 'Content-Type: application/json' \
  -d '{"displayName":"Keppra 500mg","searchTerm":"Keppra",
       "lastFilledOn":"2026-09-01","daysSupply":24}'

curl -s -b jar localhost:8080/api/me/supply-check | python3 -m json.tool
```

Note there is no `userId` anywhere — the session decides whose list this is.

```
Keppra 500mg   runs out 2026-09-25   6 days   CRITICAL  "Contact your prescriber today."
Ozempic 0.5mg  runs out 2026-12-30  102 days  WATCH     "No action needed yet."
```

Same national shortage severity, opposite urgency — **because urgency comes from the
patient's calendar, not the FDA's wording.**

## v0.2: the app now speaks first

v0.1 could answer *"is my medication short?"* when asked but couldn't **tell** anyone, so it
never delivered lead time. A nightly sync (03:00) closes that loop.

```bash
curl -X POST localhost:8080/api/admin/sync    # manual trigger
```

Three consecutive runs:

```
run 1 (no contact on file)  Alerts sent: 0 ... 1 user(s) needed an alert but no contact route
run 2 (after adding email)  sent: 1   suppressed: 0
run 3 (nothing changed)     sent: 0   suppressed: 1     <- de-duplication
```

- **A failed fetch is never an all-clear.** `ShortageCache` keeps the last good feed; a
  failed sync neither clears it nor alerts over empty data, and says so outright.
- **Empty feed ≠ failed fetch.** "Nothing is short" is a success; "couldn't reach the FDA"
  isn't. A test for each.
- **De-dup, but escalation gets through.** Shortages last months, so a memoryless job would
  email nightly until ignored. But `HIGH → CRITICAL` means days not weeks, so it re-alerts.
- **Staleness is visible.** `GET /api/sync/status` — a sync failing quietly for a week is
  the failure most likely to go unnoticed, since everything still *responds*.

03:00 because alerts composed overnight are read when a pharmacy can actually be called.

## v0.3: storage that survives a restart

v0.2's `ConcurrentHashMap` made `AlertLedger`'s de-duplication a lie: a restart emptied it,
so the next nightly run re-sent every alert the app had ever sent — the exact notification
fatigue the ledger exists to prevent. v0.3 moves medications, contacts and the ledger into
PostgreSQL.

- **Flyway owns the schema**, Hibernate runs `ddl-auto: validate`. An ORM that rewrites
  tables at startup can drop a column, and its data, with nobody approving a diff.
- **Domain records stay records.** JPA needs a no-arg constructor and mutable fields, which
  `Medication`'s validating compact constructor cannot provide — so `MedicationEntity` is a
  separate class that maps both ways. Boilerplate bought a domain object that cannot hold
  invalid state.
- **PostgreSQL is the default, with no fallback.** An unreachable database stops the app
  booting. Opposite default from the shortage feed, deliberately: a missing feed shows up in
  every response, whereas silent data loss looks like working software until a list turns up
  empty.
- **`@Enumerated(EnumType.STRING)`**, never JPA's `ORDINAL` default. Storing an enum by
  position means adding a value mid-`SupplyRisk` reinterprets every existing row — a stored
  `CRITICAL` reading back as `HIGH`, and the ledger suppressing an alert it should send.

> **What the v0.2 roadmap got wrong.** It deferred this because "no Docker daemon here, and
> H2 would be green for a reason that doesn't transfer." The H2 half still holds. The Docker
> half was false — PostgreSQL 16 was installed all along. The blocker was an unchecked
> assumption, not the environment.

Two bugs the new tests caught, both mine. The integration test **passed on an empty database
and failed on the second run**, because it committed real rows and never cleaned up; fixed
with an explicit truncate rather than `@Transactional`, since a test that never commits
cannot catch a bad column mapping. And `AlertLedgerTest`'s repeat-window test **would have
passed with the window logic deleted** — each ledger had its own store, so it could only
assert *something* was recorded. Splitting policy from storage made the honest version
writable: one store, two clocks, both sides of the boundary asserted.

## v0.4: authentication, and why the routes changed shape

v0.3's limitation table said "no authentication; `userId` is trusted in the clear". In
practice that meant **any caller could read, extend or delete anyone's medication list** by
naming their id, and `DELETE /api/medications/{id}` checked nothing at all — knowing a UUID
was enough to destroy someone else's record.

The repair is not a check on every endpoint. It is removing the thing being checked:

```
v0.3   GET /api/users/{userId}/supply-check     ← client picks whose data
v0.4   GET /api/me/supply-check                 ← the session picks
```

You cannot have an insecure direct object reference if there is no direct object reference.
A check you must remember to write on every route is a check you will one day forget on one;
a parameter that does not exist cannot be forgotten. `MedicationRequest` lost its `userId`
field for the same reason, and a test asserts that smuggling one into the JSON changes
nothing.

**Sessions, not JWTs.** A JWT cannot be revoked before it expires, and "log me out
everywhere" and "my phone was stolen" both need to take effect now. The usual fix — a
server-side denylist — reintroduces exactly the shared state that made JWTs attractive. A
session id is opaque and means nothing except as a row in a table, so revoking it is a
`DELETE`. v0.3 put PostgreSQL there anyway, and Spring Session JDBC means a deploy no longer
logs everyone out.

Other decisions worth the words:

- **A delegating password encoder**, so hashes store the algorithm that made them
  (`{bcrypt}$2a$10$…`). Switching to argon2id later then means new passwords use it and old
  ones still verify — no forced reset.
- **Passwords capped at 72 bytes and rejected above it.** BCrypt silently ignores the rest,
  so two passphrases sharing a 72-byte prefix would open the same account. Truncating
  quietly would mean storing something the person did not choose.
- **No composition rules.** NIST SP 800-63B advises against them, and the reason is
  behavioural: they reliably produce `Password1!`. Length is the requirement that helps.
- **Login answers identically** for a wrong password and an unknown user; otherwise the form
  enumerates who has an account here, which is who is managing a medication.
- **Not-yours returns 404, not 403.** A 403 confirms the id is real.
- **CSRF stays on.** "It's a JSON API" is not a reason to disable it — the browser attaches
  the session cookie to a cross-site POST regardless of content type.

> **A bug found by probing, not by reading.** With the config apparently correct, 20
> unauthenticated requests left 20 rows in `SPRING_SESSION`: Spring Security's default
> `RequestCache` stashes every rejected request in a new session so a form login can replay
> it. For a JSON API that is an unauthenticated way to grow the database. Disabling the
> request cache took it to 0, measured the same way. The regression guard lives in the CI
> smoke test rather than a unit test, because MockMvc keeps its own session bookkeeping and
> does not reproduce a real container's here — a test that passes in a simulation the
> production path does not share is worse than no test.

The 16 security tests are written as **attacks that used to succeed**, not as feature
checks. Two were verified by re-introducing the original bugs and confirming the tests go
red.

## v0.5: rate limiting, and the shape of a guessing attack

v0.4 checked passwords. Nothing limited how often they could be checked, so the protection
was only ever as strong as the weakest password any user had chosen — and an attacker had
unlimited tries to find that user.

**Two counters, and an attempt must be under both to be tried at all.**

| Counter | Limit | Window | Catches |
|---|---|---|---|
| Per username | 5 failures | 15 min | Someone hammering one account |
| Per source address | 20 failures | 15 min | One password sprayed across many accounts |
| Per source address, registration | 5 attempts | 1 h | Mass sign-up, and reading the user list out of "username taken" |

Either counter alone has a blind spot the other covers. Per-username never moves during a
spray — every account collects exactly one failure, indistinguishable from a typo.
Per-address is the wrong shape for a targeted attack and punishes a whole office behind one
address. Refusals are `429` with a `Retry-After` header saying exactly when the block lifts.

- **A window that heals, never a lockout.** Locking an account until an administrator
  intervenes turns "I know your username" into "I can take you off your medication list".
  These counters age out on their own.
- **A refused attempt is not recorded.** So an attacker who keeps knocking can neither grow
  the table nor extend the block they have put someone else behind.
- **Refused *before* the password is checked.** Verifying a BCrypt hash is deliberately
  slow, so answering a thousandth guess costs this server far more than sending it cost the
  attacker. The observable proof: once blocked, the *correct* password gets `429` too.
- **Counted identically for usernames that do not exist.** Counting only real accounts would
  make the `429` answer the question login is careful never to answer.
- **A success clears the username's counter, not the address's** — otherwise an attacker
  holding one valid account resets their own budget between bursts by logging into it.
- **The counters live in PostgreSQL.** In the JVM they would be cleared by a restart, and
  every replica behind a load balancer would get its own full budget.

> **On citations.** The usual reference for authentication throttling is NIST SP 800-63B,
> and this environment's egress policy blocks `pages.nist.gov` and the OWASP cheat sheets —
> `403` at the proxy, confirmed through two different clients. The numbers above are
> therefore argued from first principles rather than taken from a source that was actually
> read. Check them against current guidance before treating them as settled.

## How matching works

A user types `Adderall XR 10mg`. The FDA publishes
`AMPHETAMINE ASPARTATE; AMPHETAMINE SULFATE; DEXTROAMPHETAMINE SACCHARATE`. Same medicine,
zero shared words.

[`DrugNameNormalizer`](src/main/java/com/refillradar/matching/DrugNameNormalizer.java) strips
strengths, dosage forms and salts, then maps brands to ingredients. Get it wrong and the app
fails **silently** — no alerts, forever, and silence looks like good news. Hence the heaviest
test coverage in the project.

Matching is on **whole tokens, never substrings**: `amphetamine` is inside
`methamphetamine`, `codeine` inside `hydrocodone`. Tests assert those never match.

Two more decisions:

- **An unrecognised FDA status counts as active.** A false negative leaves someone confident
  and wrong — worse than having no app. A false positive costs one question to a pharmacist.
- **Time is injected.** A `Clock` bean, so `"12 days remaining is HIGH"` stays true instead
  of drifting with the calendar.

## API

| Method | Path | Auth | |
|---|---|---|---|
| `POST` | `/api/auth/register` | — | Create an account |
| `POST` | `/api/auth/login` | — | Start a session |
| `POST` | `/api/auth/logout` | session | End it |
| `GET` | `/api/auth/me` | session | Who am I |
| `POST` | `/api/medications` | session | Register a medication |
| `GET` | `/api/me/medications` | session | List |
| `DELETE` | `/api/medications/{id}` | session | Remove (yours only) |
| `GET` | `/api/me/supply-check` | session | **Main endpoint** |
| `GET` | `/api/debug/normalize?name=X` | session | How a drug name was interpreted |
| `POST` | `/api/admin/sync` | **ADMIN** | Run the nightly sync now |
| `GET` | `/api/sync/status` | session | Age of the cached feed |
| `POST`/`GET` | `/api/me/contact` | session | Where alerts go / is the user reachable |

**`/api/users/{id}/...` is gone.** Not guarded — gone. See the v0.4 section below.
`register` and `login` also answer **`429 Too Many Requests`** with a `Retry-After` header
once the limits in the v0.5 section are reached.

`/api/debug/normalize` exists because the worst failure here is silent: if the normaliser
has never heard of a brand, nothing matches and the app looks fine while protecting nobody.

```bash
curl -sG localhost:8080/api/debug/normalize --data-urlencode "name=Dexilant 60mg"
# {"tokens":["dexilant"],"recognisedBrand":false}   <-- needs the generic name
```

## Why tests never touch the network

`ShortageSource` has two implementations: `FixtureShortageSource` (recorded JSON, the
default) and `OpenFdaShortageSource` (live). Switch with
`refillradar.shortage-source=openfda`.

The immediate reason is that this build environment blocks `api.fda.gov`. The lasting one:
a suite calling a third-party API is slow, rate-limited, and red when someone else has an
outage. It's also the only way to test the awkward cases — the fixture deliberately contains
an unrecognised status, a missing brand name, a resolved shortage and a combination product.

Defaulting to `fixture` means a misconfigured deploy fails towards **obviously fake** data
(every response says `NOT LIVE`) rather than silently broken live data.

## Deployment

> **Upgrading from v0.3: `V2` deletes existing rows.** Pre-v0.4 medications and contacts
> carry a `user_id` that was an unauthenticated free-text string, not an identity — there is
> no account they could belong to, and inventing one would be worse than dropping them. This
> is safe only because no deployment has ever held real data; on a system that had, that step
> would be a reconciliation exercise rather than a `DELETE`.

### Local

```bash
# 1. Database. These values match the defaults in application.yml.
sudo -u postgres psql -c "CREATE USER refillradar WITH PASSWORD 'refillradar'"
sudo -u postgres createdb -O refillradar refillradar

# 2. Run. Flyway creates the schema on first start; Hibernate then validates against it.
mvn package && java -jar target/refillradar-0.1.0-SNAPSHOT.jar

# Overrides, all optional:
java -jar target/*.jar --refillradar.shortage-source=openfda      # live FDA API
java -jar target/*.jar --spring.profiles.active=memory            # no database, loses data
java -jar target/*.jar \
  --refillradar.security.login-throttle.max-failures-per-username=5 \
  --refillradar.security.login-throttle.max-failures-per-ip=20 \
  --refillradar.security.login-throttle.window=PT15M              # rate limits, defaults shown
```

Override with `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD`. **Passwords go in a
secret manager, never in the image or the repo** — the defaults above exist so a laptop
works out of the box and are not credentials for anything real.

**Behind a reverse proxy, set `FORWARD_HEADERS_STRATEGY=framework`** — and only there.
The per-address limit counts the socket's address, which a client cannot forge. Put a proxy
in front without telling the application, and every request appears to come from the proxy:
twenty failures from anyone locks out everyone. Set it when the proxy *overwrites*
`X-Forwarded-For`; set it with a proxy that merely passes the header through and an attacker
rotates it for a fresh budget, or forges yours and spends it.

**`SESSION_COOKIE_SECURE=true` is required for any deployment served over HTTPS.** It
defaults to `false` so `http://localhost` can log in at all; left false behind TLS, the
session cookie — which *is* the credential — can travel in plaintext. The application logs
a warning naming this setting on every boot where it is false, so the insecure state is
visible in the logs rather than buried in a config file nobody re-reads.

### Container

```bash
docker network create rr-net
docker run -d --name rr-db --network rr-net \
  -e POSTGRES_DB=refillradar -e POSTGRES_USER=refillradar \
  -e POSTGRES_PASSWORD=refillradar postgres:16

docker build -t refillradar . && docker run -p 8080:8080 --network rr-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://rr-db:5432/refillradar refillradar
```

Multi-stage build, JRE-only runtime stage, non-root user. No database config is baked in —
it arrives as environment variables, so one artefact runs in every environment.

### CI

[`refillradar-ci.yml`](../../.github/workflows/refillradar-ci.yml): build + test against a
`postgres:16` service → upload reports (`if: always()`) → build container (`needs: build`)
→ start a database container and smoke-test the image against it. The smoke test asserts
that anonymous access is refused, that ten anonymous requests create zero session rows, that
a sixth wrong password gets `429` while recording only five attempts, and that Flyway created
every table the application expects.

Both jobs **wait for readiness** rather than hoping (`pg_isready` as the service health
check, and polled again before the app starts) — same class of race as CI bug #2 below.

> **Two CI bugs worth learning from.**
>
> **1.** The container job was first gated on `master`/`main`, so it *skipped* on PRs —
> meaning the image would only be verified *after* the merge it was meant to protect. The
> right split is **verify everywhere, publish only from main**; it's the registry push that
> takes a branch gate.
>
> **2.** Once it ran, it failed with `curl: (56) Recv failure: Connection reset by peer`,
> 150ms in. `--retry-connrefused` covers `ECONNREFUSED` only — and while a container is up
> but hasn't bound its port, Docker's proxy *accepts* then resets. **The same command passed
> locally**, because with no proxy the race gives error 7 (refused), which that flag *does*
> handle. Green locally for a reason that didn't transfer. Fixed by waiting for the startup
> log line, adding `--retry-all-errors`, and dumping `docker logs` on every failure path.

Secrets go in GitHub Actions Secrets, never the repo. Tag images with the commit SHA, never
`latest` alone — you can't roll back to "latest".

## Limitations

| # | Limitation | Impact |
|---|---|---|
| 1 | **Never verified against the live FDA API** — `api.fda.gov` unreachable here | Field names, date formats and the `status` vocabulary need confirming on first live run |
| 2 | **Brand mapping is 20 hand-seeded entries.** Real answer is RxNorm | Unknown brands cause **false negatives** — the dangerous direction. Mitigated by `/debug/normalize` |
| 3 | ~~No authentication~~ — **resolved in v0.4** | |
| 4 | ~~In-memory storage~~ — **resolved in v0.3** | |
| 5 | ~~No scheduled sync~~ — **resolved in v0.2** | |
| 6 | **Fixture is hand-written**, not a real capture | Labelled in the file itself |
| 7 | **Email never tested against real SMTP** | Defaults to `alert-channel=log`, which reports `delivered: false` rather than pretending. Check spam placement on first live run |
| 8 | ~~Alert ledger is in memory~~ — **resolved in v0.3** | |
| 9 | **No backups, no retention policy, no encryption at rest** | The database now holds medication lists, which are health data. Storing it is a new obligation, not just a new feature |
| 10 | ~~No rate limiting on login~~ — **resolved in v0.5** | |
| 11 | **No password reset, no email verification, no account deletion** | A forgotten password is unrecoverable, and there is no way to exercise a deletion request |
| 12 | **No admin can be created over HTTP, by design** | The first one has to be promoted with SQL: `UPDATE users SET role='ADMIN' WHERE username='…'` |
| 13 | **Rate limiting does not stop a distributed attack** | A few guesses from each of thousands of addresses stays under both limits. No CAPTCHA, no proof of work, no second factor — and the user is never told someone is trying |

### Roadmap

v0.5 shipped rate limiting. RxNorm stays deferred for a checked reason:
`rxnav.nlm.nih.gov` returns `403` at this environment's egress proxy.

- **v0.6** — password reset, email verification and account deletion (11); telling a user
  their account is being guessed at (13)
- **v0.7** — retention, backups and encryption at rest (9)
- **v0.8** — RxNorm (2); SMS via Twilio; real recorded fixture (6)

**On SMS:** this audience skews older and email is the channel they're least reliably on.
SMS reaches more of them but needs A2P 10DLC registration — procurement, not code. Email
first is honest sequencing, not a claim of parity.

**Non-goals** — suggesting alternative medicines (clinical decision), pharmacy stock levels,
anything that makes this a regulated medical device.

## Sources

[openFDA Drug Shortages API](https://open.fda.gov/apis/drug/drugshortages) ·
[searchable fields](https://open.fda.gov/apis/drug/drugshortages/searchable-fields/) ·
[FDA Drug Shortages](https://www.fda.gov/drugs/drug-safety-and-availability/drug-shortages) ·
[ASHP statistics](https://www.ashp.org/drug-shortages/shortage-resources/drug-shortages-statistics) ·
[Becker's — shortages trending upward 2026](https://www.beckershospitalreview.com/pharmacy/us-drug-shortages-trending-upward-in-2026/)
