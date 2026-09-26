# RenewalGuard

**Stop people losing benefits they still qualify for.** A deadline-defence app for Medicaid
renewals — because most people who lose coverage never became ineligible, they just missed a
letter.

> **v0.2 — persistence and identity.** 90 tests. Cases live in PostgreSQL, every read is
> scoped to the logged-in owner, and logins are rate limited. Still not production ready: no
> actual sending, and see [Limitations](#limitations) for what auth does *not* cover.
>
> **Not an eligibility decision.** Only your state agency decides whether you qualify. Your
> own renewal notice always outranks this app.

Research: [`../../proposals/03-renewalguard.md`](../../proposals/03-renewalguard.md)

## Why

- **Up to 70% of Medicaid terminations during redetermination are _procedural_** — a missed
  deadline, an undelivered notice, paperwork never returned — **not a finding that someone
  stopped qualifying.**
- During the pandemic unwinding, **~69%** of people who lost Medicaid were dropped for
  procedural reasons. Two figures, different periods and sources, same magnitude.
- **14 states give only 10 days to respond** when documents are required.

**Read that again.** If ~70% of terminations are procedural, the dominant failure mode of
the US safety net is not fraud and not overspending. **It is stationery.** A letter goes to
an old address. A form asks for a pay stub from a job someone left. Coverage ends for
someone who was, throughout, eligible — then they re-apply, get re-approved, and the state
pays to process them twice.

### It's about to get harder

**Public Law 119-21 moves Medicaid expansion adults from annual to six-month renewals**, for
renewals scheduled **on or after 1 January 2027**. CMS issued guidance to state Medicaid
directors on **6 March 2026**. Children, pregnant women, seniors and disabled enrollees stay
annual — reporting puts the affected group at **a little over a quarter of all enrollees**.

So from 2027 a household can hold two people, same programme and same agency, with deadlines
arriving at different rates. Twice the renewals, against a baseline where missing one is
already the leading cause of losing coverage.

*⚠️ Sources cite this as both "Section 44108" and "Section 71107" of P.L. 119-21 (both
amending SSA §1902(e)(14)). The substance is consistent; the section number isn't.*

## Quick start

JDK 21+, Maven 3.9+, Docker (for PostgreSQL). No network. Port **8082**.

Since v0.2 this needs a database and a login. Three commands, in order — the shared auth
module has to be installed first, because `com.commonauth:common-auth` is published nowhere
and is built from this repository:

```bash
docker run -d --name renewalguard-db -p 5432:5432 \
  -e POSTGRES_DB=renewalguard -e POSTGRES_USER=renewalguard \
  -e POSTGRES_PASSWORD=renewalguard postgres:16

mvn -f ../../libs/common-auth/pom.xml install -DskipTests   # once, and after any change to it
mvn test                                                    # 90 tests
mvn spring-boot:run
```

No database to hand? `mvn spring-boot:run -Dspring-boot.run.profiles=memory` starts with
in-memory stores. Data is lost on restart, so it is a demo, not a deployment — and
`MemoryProfileTest` runs on every build so this instruction cannot quietly stop working.

Register, log in, and keep the cookie jar. The `XSRF-TOKEN` cookie must be echoed back as a
header on every write; that is CSRF protection, not ceremony:

```bash
curl -sS -c jar -X POST localhost:8082/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"robert","password":"a-long-enough-passphrase"}'

curl -sS -c jar -b jar -X POST localhost:8082/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"robert","password":"a-long-enough-passphrase"}'

XSRF=$(grep XSRF-TOKEN jar | awk '{print $7}')
```

Then track a case. Note there is no `userId` in the payload: the owner comes from the session.

```bash
ID=$(curl -sS -b jar -X POST localhost:8082/api/cases \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" -d '{
  "stateCode":"CA","category":"EXPANSION_ADULT",
  "renewalDueOn":"2026-10-05"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -s -b jar "localhost:8082/api/me/cases/$ID/status"   | python3 -m json.tool
curl -s -b jar "localhost:8082/api/me/cases/$ID/reminder" | python3 -m json.tool
```

```
due 2026-10-05  ->  15 days  [HIGH] Act this week
documents ready by : 2026-09-25        <- works BACKWARDS from the deadline
cadence            : every 12 months -> next 2027-10-05
ladder             : [60, 45, 30, 21, 14, 10] then daily
address check      : True

CAVEAT: From 2027, adults covered through Medicaid expansion renew every SIX months...
```

Note the nuance: *this* renewal is annual because it falls in 2026 — but the caveat still
fires, because the **next** one crosses into the new rule. Warning in advance is the point.

## Four decisions

**1. The deadline is not the deadline.** `documentsReadyBy` works *backwards* by the
response window. If an agency asks for a pay stub with 10 days to reply, you need it
**before** the request lands.

**2. Assume the shortest window, and say so.** Reporting says 14 states give only 10 days —
but **I don't know which 14**, and inventing a state list would be fabrication. So it assumes
10 days unless you enter your own from your notice. The asymmetry is the argument: *assuming
30 days when you have 10 loses your coverage; assuming 10 when you have 30 just means you
finish early.*

**3. A passed deadline is CRITICAL, not dismissed.** Unlike RefillRadar — where a passed
date usually meant stale data — here it's the emergency, and reinstatement is often still
possible:

> *"This does NOT automatically mean you have lost coverage… Many people are dropped for
> paperwork reasons while still qualifying. Call your state agency today."*

**4. Escalation, not repetition.** Reminders every day for two months train people to ignore
them — and an ignored reminder carries false reassurance. Sparse far out (60/45/30/21/14),
daily inside the final 10.

### Three copy rules, enforced by tests

1. **Never say whether someone qualifies.** Implying "you probably won't qualify anyway"
   could talk someone out of a renewal they'd have won — the exact harm this app prevents,
   delivered by the app itself.
2. **Never ask for sensitive data,** and say so. This audience is heavily targeted by
   benefits scams; a legitimate service behaving like one teaches the wrong reflex.
3. **Always defer to the notice.** *"If our date and your notice disagree, believe the notice."*

### Why there's no document locker

The proposal called for an encrypted locker holding photos of pay stubs, leases and IDs.
**Deliberately not built.** Storing identity documents for low-income households concentrates
exactly what an identity thief wants, belonging to people least able to absorb it — and doing
it responsibly needs key management, retention limits and a security review.

v0.1 ships the ~80% of value with none of that risk: **a checklist saying what to find and
where it usually lives.** It also collects **no SSN, income, household composition or
immigration status** — a deadline tracker has no business holding eligibility data.

## API

Everything except registration and login needs a session. Writes also need the CSRF header.

| Method | Path | |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account |
| `POST` | `/api/auth/login` | Start a session |
| `POST` | `/api/auth/logout` | End it, server-side |
| `POST` | `/api/cases` | Start tracking a case — owner comes from the session |
| `GET` | `/api/me/cases` | List **your own** cases |
| `GET` | `/api/me/cases/{id}/status` | **Main endpoint** — deadline, urgency, checklist, caveats |
| `GET` | `/api/me/cases/{id}/reminder` | The exact message that would be sent |
| `DELETE` | `/api/me/cases/{id}` | Stop tracking |

### What v0.2 changed, and why it is a removal rather than a check

v0.1 trusted the caller to say whose data they wanted:

```
v0.1   POST /api/cases          {"userId": "...", ...}   <- client picks the owner
v0.2   POST /api/cases          {...}                    <- the session does

v0.1   GET  /api/users/{id}/cases                        <- client picks whose
v0.2   GET  /api/me/cases                               <- the session does

v0.1   GET  /api/cases/{id}/status                       <- no check whatsoever
v0.2   GET  /api/me/cases/{id}/status                    <- owner is in the SQL WHERE clause
```

A case id still appears in the path, because unlike SafeWord — where one account has exactly
one circle — a person here genuinely tracks several and has to name one. So this is the other
half of the fix: the identifier stays, and `BenefitCaseRepository` simply **does not offer a
method that looks a case up without an owner**. A controller check is one `if` a future handler
can forget; a missing method is a compile error.

**Someone else's case returns 404, never 403.** A 403 would confirm the id is real, and "real,
just not yours" is exactly what an attacker enumerating ids wants to learn — "this person has a
Medicaid case" is itself the sensitive fact here. Both the unit suite and the CI smoke test
assert that a real-but-not-yours id answers byte-for-byte identically to a fabricated one.

Three of these controls were verified by putting the original bug back and confirming the tests
go red: the owner-blind lookup (2 tests failed, mallory got `200` on alice's case), the
owner-blind delete (`204` — the case was actually destroyed), and the in-memory store's
ownership filter. A test that has never failed is a test whose value is unmeasured.

## Deployment

### Local

```bash
# 1. The shared auth module, into your local Maven repository. Required first.
mvn -f ../../libs/common-auth/pom.xml install -DskipTests

# 2. A database.
docker run -d --name renewalguard-db -p 5432:5432 \
  -e POSTGRES_DB=renewalguard -e POSTGRES_USER=renewalguard \
  -e POSTGRES_PASSWORD=renewalguard postgres:16

# 3. The app. Flyway migrates on startup; Hibernate then validates against the result and
#    refuses to boot on a mismatch, so a schema drift is a failed start, not a silent bug.
mvn package && java -jar target/renewalguard-0.1.0-SNAPSHOT.jar
```

### Container

**Build from the repository root, not from this directory:**

```bash
cd ../..                                                     # repo root
docker build -f apps/renewalguard/Dockerfile -t renewalguard .
```

The trailing `.` is load-bearing. The image needs `libs/common-auth`, and a build context
rooted at `apps/renewalguard/` cannot see a sibling directory — Docker would fail resolving
`com.commonauth:common-auth`, which is published nowhere. The Dockerfile installs the module
inside its build stage before packaging the app.

```bash
docker network create renewalguard-net
docker run -d --name renewalguard-db --network renewalguard-net \
  -e POSTGRES_DB=renewalguard -e POSTGRES_USER=renewalguard \
  -e POSTGRES_PASSWORD=renewalguard postgres:16

docker run -p 8082:8082 --network renewalguard-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://renewalguard-db:5432/renewalguard \
  -e SPRING_DATASOURCE_USERNAME=renewalguard \
  -e SPRING_DATASOURCE_PASSWORD=renewalguard \
  -e SESSION_COOKIE_SECURE=true \
  renewalguard
```

### Settings you must get right in production

| Variable | Default | Set it to |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | local Postgres | your database |
| `SESSION_COOKIE_SECURE` | `false` | **`true`** anywhere served over HTTPS — otherwise the session cookie travels in the clear on any accidental `http://` request. The app logs a warning on every boot where it is false, so the insecure setting is visible rather than buried in a file |
| `FORWARD_HEADERS_STRATEGY` | `none` | `framework` **only** behind a proxy you control that overwrites `X-Forwarded-For`. The login throttle counts per source address; with `none` that address is the socket's, which a client cannot forge. Set it without such a proxy and anyone can rotate the header for a fresh budget, or forge someone else's address and spend theirs |

### Cloud CI/CD

[`renewalguard-ci.yml`](../../.github/workflows/renewalguard-ci.yml) runs on every push and PR
touching `apps/renewalguard/**`, `libs/common-auth/**` or the workflow itself. Two jobs:

1. **Build and test** — installs `libs/common-auth`, then `mvn verify` against a real
   `postgres:16` service container. The service is needed because the persistence tests assert
   things a `HashMap` passes trivially and PostgreSQL might not: a nullable `DATE` surviving a
   round trip, an enum stored by name rather than ordinal, and the `CHECK` constraints on
   `state_code` and `response_window_days` actually firing.
2. **Build container image** (`needs: build`) — builds from the repo root, starts its own
   PostgreSQL on a user-defined network, and smoke-tests the running container: anonymous
   requests get `401`, ten of them create **0** session rows, two users register and log in,
   a case round-trips with its response window intact, **mallory cannot read, delete or list
   alice's case**, the refusal is byte-identical to one for a fabricated id, a tokenless write
   gets `403`, a sixth wrong password gets `429` (and so does the *correct* one, which is
   observable proof the refusal happens before the password is checked), and Flyway's tables
   exist with no column able to hold an SSN, income, household or immigration field.

Neither job is branch-gated, deliberately. **Verify everywhere, publish only from `main`** — an
image checked only on `main` is checked after the merge it was meant to protect. The registry
push is what belongs behind a branch gate, and it is not wired up yet; when it is, tag with the
commit SHA, never `latest` alone, because you cannot roll back to `latest`.

The `--retry-all-errors` curl flag in the smoke test is load-bearing: `--retry-connrefused`
alone does not cover curl error 56 (connection reset), which is what Docker's port proxy returns
while the container is up but the app has not yet bound its port. See
[RefillRadar's CI notes](../refillradar/README.md#deployment) for the incident.

## Limitations

| # | Limitation | Impact |
|---|---|---|
| 1 | **Response-window figures single-source**, unverified against regulation text | They shape reminder cadence only, never quoted as legal fact |
| 2 | **No document storage** | Deliberate — see above. Checklist only |
| 3 | ~~No authentication~~ | **Fixed in v0.2.** Sessions, BCrypt, a login throttle, and every read scoped to the session's owner |
| 4 | ~~In-memory storage~~ | **Fixed in v0.2.** PostgreSQL with Flyway; an unreachable database stops the app starting rather than silently losing deadlines |
| 4a | **Rate limiting does not stop a distributed attack** | A few guesses from each of thousands of addresses stays under both limits. No CAPTCHA, no second factor, and the user is never told someone is trying |
| 4b | **No password reset, email verification or account deletion** | And the first admin has to be promoted with SQL |
| 4c | **No backups, retention policy or encryption at rest** | For data that records who is enrolled in Medicaid |
| 5 | **No actual sending.** Reminders are composed and previewable only | The scheduled sweep + SMS/email is v0.2. **SMS is mandatory, not optional** — this audience is least reachable by email |
| 6 | **SNAP not modelled** | Accepted and flagged, never silently given Medicaid's cadence |
| 7 | **Renewal date is user-entered** — no agency API exists | If their notice disagrees, the notice wins, and the app says so |

### Roadmap

- ~~**v0.2** — PostgreSQL (4); auth (3)~~ — **done.** Both arrived together, because neither
  is much use alone: accounts that vanish on restart are not accounts, and durable cases nobody
  owns are still readable by anyone who guesses an id
- **v0.3** — daily sweep + Twilio SMS and email with delivery receipts (5); distributed-attack
  defences (4a); password reset and account deletion (4b)
- **v0.4** — SNAP cadence (6); per-state response windows once verified (1); encrypted locker
  behind a security review (2)

**Non-goals** — determining eligibility; auto-filing renewals; collecting SSN or income.

## A note on stack choice

The proposal recommended **Python/Django**, and that was sound — Django's admin, auth and
forms handling suit something that's fundamentally *forms, users and deadlines*.

It's built in **Java/Spring Boot** anyway: one language and one CI pattern across the repo,
matching the Java already here. The proposal flagged this as a judgement call, not a
correctness issue. **BirthPath is Python**, where the geospatial case is decisive rather than
marginal — variety where it's justified, not for its own sake.

## Sources

[Commonwealth Fund — Reducing Medicaid Churn](https://www.commonwealthfund.org/publications/issue-briefs/2025/jun/reducing-medicaid-churn-policies-promote-stable-health-coverage) ·
[CMS SMD# 26-001](https://www.medicaid.gov/federal-policy-guidance/downloads/smd26001.pdf) ·
[Georgetown CCF — 6-month renewals](https://ccf.georgetown.edu/2026/03/06/cms-releases-guidance-on-6-month-medicaid-renewals-for-expansion-adults/) ·
[KFF — eligibility and renewal policies](https://www.kff.org/medicaid/medicaid-and-chip-eligibility-enrollment-and-renewal-policies-as-states-prepare-for-major-medicaid-policy-changes/) ·
[Urban Institute — more-frequent redeterminations](https://www.urban.org/urban-wire/more-frequent-medicaid-redeterminations-would-reduce-health-insurance-coverage-and)
