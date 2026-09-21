# RefillRadar

**Personal early warning for drug shortages.** Watches the US FDA shortage database against
*your* medication list and warns you weeks before a refill fails — while there is still time
to do something about it.

> **Status: v0.2 — the alerting loop is closed.** 105 tests passing. Runs offline. Not
> production ready: no authentication, no database, no live API verification. See
> [Limitations](#limitations).
>
> **Not medical advice.** RefillRadar reports entries in a federal database. It never
> suggests alternative medicines. Always speak to your pharmacist or prescriber.

Problem research and competitive analysis: [`../../proposals/02-refillradar.md`](../../proposals/02-refillradar.md)

---

## Why this exists

The FDA publishes its shortage list. ASHP publishes one. Both are public. But what actually
happens to a patient is:

> Turn up at the pharmacy the day you run out → *"we can't get it"* → now you need a
> prescriber appointment, an alternative, and possibly prior authorisation — **starting from
> zero, with zero doses left.**

The information existed for weeks. It never reached the one person whose refill date made it
urgent. **The data is public; the personalisation is missing.** The unique input RefillRadar
has, and a shortage list does not, is *your refill date*.

Context: ASHP counted **227 active US shortages** at the end of Q2 2026 — a third consecutive
quarterly increase. CNS medications are the largest category, which includes ADHD medication,
antidepressants and anti-epileptics, where an interrupted supply is a medical event rather
than an inconvenience.

---

## What v0.2 added: the app now speaks first

v0.1 could answer *"is my medication short?"* when asked. It could not **tell** anyone —
which meant it never actually delivered lead time, the one thing it promises. v0.2 closes
that loop with a nightly sync.

```bash
curl -X POST localhost:8080/api/admin/sync
```

Real output from three consecutive runs:

```
run 1 (no contact route on file)
  Fetched 8 records... Alerts sent: 0 ... 1 user(s) needed an alert but have no contact route.

run 2 (after adding an email)
  sent: 1   suppressed: 0

run 3 (nothing changed)
  sent: 0   suppressed: 1        <- de-duplication
```

### Four decisions in that loop

**1. A failed fetch is never an all-clear.** `ShortageFetchException`'s docs always said a
failure must leave previous data in place — until v0.2 that was only a comment, because
there was nothing to leave. Now `ShortageCache` holds the last good feed, a failed sync
does **not** clear it and does **not** run an alerting pass over empty data, and the
response says outright: *"This is NOT an all-clear."*

**2. An empty feed and a failed fetch are different things.** "The FDA says nothing is
short" is a success. "We couldn't reach the FDA" is not. Conflating them is the whole
failure mode this app exists to avoid, and there's a test for each.

**3. De-duplication, but not at the cost of new information.** Shortages last months. A
nightly job with no memory would email the same person every night until they stopped
reading — *an ignored alert is worse than none, because it still implies someone is
watching.* But a naive ledger is also wrong: **escalation gets through.** Going from `HIGH`
to `CRITICAL` means days left rather than weeks, and suppressing that would be the worst
possible behaviour.

**4. Staleness is visible.** A sync failing quietly for a week is the failure most likely to
go unnoticed — everything still *responds*, just with old data. `GET /api/sync/status`
exists so monitoring can watch it.

### Why 03:00

Alerts composed overnight are read in the morning, when a pharmacy or prescriber can
actually be called. An alert at 9pm is one the reader can do nothing about for twelve
hours — that converts useful information into a night of worry.

---

## Quick start

**Prerequisites:** JDK 21+, Maven 3.9+ (or use the wrapper). No database. No network. No API key.

```bash
git clone https://github.com/bbobbylon/myfirstrepository.git
cd myfirstrepository/apps/refillradar

mvn test          # 105 tests, runs fully offline, ~10 seconds
mvn spring-boot:run
```

The app starts on **http://localhost:8080** using bundled sample shortage data.

### Try it

```bash
# 1. Add a medication that is nearly out
curl -X POST localhost:8080/api/medications \
  -H 'Content-Type: application/json' \
  -d '{"userId":"robert","displayName":"Keppra 500mg","searchTerm":"Keppra",
       "lastFilledOn":"2026-09-01","daysSupply":24}'

# 2. Add one with plenty left
curl -X POST localhost:8080/api/medications \
  -H 'Content-Type: application/json' \
  -d '{"userId":"robert","displayName":"Ozempic 0.5mg","searchTerm":"Ozempic",
       "lastFilledOn":"2026-09-01","daysSupply":120}'

# 3. Run the check
curl -s localhost:8080/api/users/robert/supply-check | python3 -m json.tool
```

Real output from that sequence:

```json
{
  "userId": "robert",
  "checkedAt": "2026-09-19",
  "source": "Recorded FDA sample data (NOT LIVE - development fixture)",
  "medicationsChecked": 2,
  "shortagesScanned": 8,
  "matches": [
    {
      "medication": "Keppra 500mg",
      "fdaRecord": "Keppra",
      "matchedOn": "keppra",
      "runOutDate": "2026-09-25",
      "daysRemaining": 6,
      "risk": "CRITICAL",
      "recommendedAction": "Contact your prescriber or pharmacist today.",
      "shortageReason": "Shortage of an active ingredient",
      "uncertainStatus": false
    },
    {
      "medication": "Ozempic 0.5mg",
      "fdaRecord": "Ozempic",
      "runOutDate": "2026-12-30",
      "daysRemaining": 102,
      "risk": "WATCH",
      "recommendedAction": "No action needed yet - we will keep watching."
    }
  ]
}
```

Same national shortage severity, opposite urgency — **because urgency comes from the
patient's calendar, not the FDA's wording.** That is the whole idea in one response.

---

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/medications` | Register a medication |
| `GET` | `/api/users/{userId}/medications` | List a user's medications |
| `DELETE` | `/api/medications/{id}` | Remove a medication |
| `GET` | `/api/users/{userId}/supply-check` | Run a check — the main endpoint |
| `GET` | `/api/debug/normalize?name=X` | Diagnostic: how a drug name was interpreted |
| `POST` | `/api/admin/sync` | **v0.2** — run the nightly sync now |
| `GET` | `/api/sync/status` | **v0.2** — age of the cached feed, for monitoring |
| `POST` | `/api/users/{userId}/contact` | **v0.2** — where alerts should go |
| `GET` | `/api/users/{userId}/contact` | **v0.2** — whether the user is reachable at all |

That last endpoint exists because of the failure mode this app most needs to avoid. If the
normaliser has never heard of a brand, nothing matches, and the app looks like it is working
while protecting nobody. Try it:

```bash
curl -sG localhost:8080/api/debug/normalize --data-urlencode "name=Adderall XR 10mg"
# {"input":"Adderall XR 10mg","tokens":["adderall","amphetamine"],"recognisedBrand":true}

curl -sG localhost:8080/api/debug/normalize --data-urlencode "name=Dexilant 60mg"
# {"input":"Dexilant 60mg","tokens":["dexilant"],"recognisedBrand":false}   <-- needs generic name
```

---

## How it works

```
Your medications          FDA shortage feed
  "Adderall XR 10mg"        "AMPHETAMINE ASPARTATE; AMPHETAMINE SULFATE..."
        |                            |
        +----> DrugNameNormalizer <--+     strips strengths, dosage forms, salts;
        |      {adderall,             |     maps brands to ingredients
        |       amphetamine}          |
        |                            |
        +--------> ShortageMatcher <-+     whole-token intersection (never substring)
                        |
                   RefillProjector          lastFilledOn + daysSupply vs today
                        |
                   SupplyRisk               CRITICAL / HIGH / MEDIUM / WATCH
                        |
                   AlertComposer            wording that routes to a human
```

### The hard part is name matching

A user types `Adderall XR 10mg`. The FDA publishes
`AMPHETAMINE ASPARTATE; AMPHETAMINE SULFATE; DEXTROAMPHETAMINE SACCHARATE`. **Same medicine,
zero shared words.**

Think of two guest lists at a door. You are looking for names on both — but the same guest is
"Robert" on one and "Bob" on the other. [`DrugNameNormalizer`](src/main/java/com/refillradar/matching/DrugNameNormalizer.java)
is the ID check. Get it wrong and the app fails **silently**: no alerts, forever, and silence
looks exactly like good news. That is why it has the heaviest test coverage in the project.

Matching is on **whole normalised tokens, never substrings** — `amphetamine` is a substring of
`methamphetamine`, and `codeine` of `hydrocodone`. Those are different drugs, and there are
explicit tests asserting they never match.

### Three design decisions worth knowing

**1. An unrecognised FDA status counts as *active*.** If the FDA sends a `status` string we
cannot parse, we warn anyway and flag the uncertainty. The two error types are wildly
asymmetric: a false negative means someone walks into the pharmacy confident and wrong — the
app made them *worse off* than having no app. A false positive means an unnecessary question
to a pharmacist. See [`ShortageStatus`](src/main/java/com/refillradar/domain/ShortageStatus.java).

**2. "No matches" is an explicit result, not an empty response.** Every check returns
`checkedAt`, `source` and counts even when nothing matched, because *"we checked and you are
fine"* and *"the sync failed and we know nothing"* must never look the same.

**2b. An outage returns `503` with an explicit "this is NOT an all-clear".** Found during
development: pointing the app at the live API from a network that blocks it produced an
unhandled `500`. The behaviour underneath was right — it failed *loudly* rather than
returning an empty list that would read as "no shortages found" — but a bare `500` leaves a
client guessing, and a guessing client eventually guesses "probably fine". See
[`ShortageSourceExceptionHandler`](src/main/java/com/refillradar/api/ShortageSourceExceptionHandler.java).

**3. Time is injected, not ambient.** Nothing calls `LocalDate.now()`. A `Clock` bean is
injected, so tests freeze time at 19 September 2026 and assertions like *"12 days remaining is
HIGH risk"* stay true forever instead of drifting. See [`RefillProjector`](src/main/java/com/refillradar/refill/RefillProjector.java).

---

## Why the tests never touch the network

`ShortageSource` has two implementations:

| | Used in | Data |
|---|---|---|
| `FixtureShortageSource` | tests, local dev **(default)** | recorded JSON on the classpath |
| `OpenFdaShortageSource` | production | live `api.fda.gov` |

Switch with `refillradar.shortage-source=openfda`.

**The immediate reason:** the environment this was built in blocks `api.fda.gov` at an egress
proxy (only `github.com` and package registries are allowed). Without this seam, nothing could
have been built or verified at all.

**The reason it should stay even with full network access:** a suite that calls a third-party
API is slow, rate-limited, and goes red when someone else's server has a bad day. A CI signal
nobody trusts is worse than no CI at all. It is also the only way to test the awkward cases —
the fixture deliberately contains an unrecognised status, a missing brand name, a resolved
shortage and a combination product, none of which you can ask the FDA to produce on demand.

The default is `fixture`, so a **misconfigured deployment fails towards obviously fake data**
(every response says `NOT LIVE`) rather than towards silently broken live data.

---

## Deployment

### Local

```bash
mvn test                  # run the suite
mvn spring-boot:run       # dev server with live reload of config
mvn package               # build the JAR
java -jar target/refillradar-0.1.0-SNAPSHOT.jar
```

Point it at the live FDA API:

```bash
java -jar target/refillradar-0.1.0-SNAPSHOT.jar \
  --refillradar.shortage-source=openfda
```

### Docker

```bash
docker build -t refillradar .
docker run -p 8080:8080 refillradar

# with the live API
docker run -p 8080:8080 -e REFILLRADAR_SHORTAGE_SOURCE=openfda refillradar
```

The [`Dockerfile`](Dockerfile) is a **multi-stage build**: stage 1 has Maven and the JDK
(hundreds of MB), stage 2 copies only the JAR onto a JRE base. The build toolchain never
reaches production. It also runs as a **non-root user**.

✅ **Verified in CI.** Every PR builds this image, starts the container, and curls it — see
the `Build container image` job. Confirmed on
[PR #1](https://github.com/bbobbylon/myfirstrepository/pull/1):

```
Waiting for application startup...
Application started after ~4s
--- GET /api/users/ci-smoke-test/supply-check ---
{"userId":"ci-smoke-test","checkedAt":"2026-09-19","source":"Recorded FDA sample data (NOT LIVE - development fixture)","medicationsChecked":0,"shortagesScanned":8,"matches":[]}
Smoke test passed: container builds, boots as non-root, and serves a real response.
```

### Cloud CI/CD — GitHub Actions

[`.github/workflows/refillradar-ci.yml`](../../.github/workflows/refillradar-ci.yml) runs on
every push and PR touching `apps/refillradar/**`:

1. **Build and test** — JDK 21, `mvn -B verify`, `~/.m2` cached on `pom.xml`
2. **Upload test reports** — with `if: always()`, so reports exist when tests *fail*, which is
   when you need them
3. **Build container** — only on `master`/`main`, and only `needs: build`, so an image is never
   built from code that failed its tests
4. **Smoke test the image** — actually starts the container and curls it. A green unit suite
   does not prove the thing boots.

> **A mistake worth learning from.** The first version of this workflow gated the container
> job on `master`/`main`. On [PR #1](https://github.com/bbobbylon/myfirstrepository/pull/1) it
> duly **skipped** — meaning the image would only have been verified *after* the merge it was
> supposed to protect. Finding out your container is broken once it is already on the main
> branch is the opposite of what CI is for.
>
> The right split is **verify everywhere, publish only from main.** Building and smoke-testing
> costs a minute on every PR; it is the *registry push* that belongs behind a branch gate.
>
> **And then it caught a real bug on its very first run**, which is the better half of the
> story. The smoke test failed with `curl: (56) Recv failure: Connection reset by peer`,
> 150ms after `docker run` returned. `--retry-connrefused` covers `ECONNREFUSED` **only**,
> and a reset is not a refusal — while the container is up but the JVM has not bound 8080,
> Docker's port proxy *accepts* the connection and then resets it. curl treated that as
> fatal and never retried.
>
> The part worth keeping: **the same command passed when run by hand locally.** With no proxy
> in front, the identical race produces error 7 (refused), which `--retry-connrefused` does
> handle. The local check was green for a reason that did not transfer into a container —
> exactly the gap the job existed to close, found only because the job had stopped skipping.
>
> Fixed by waiting for `Started RefillRadarApplication` in the container logs before curling,
> adding `--retry-all-errors`, and dumping `docker logs` on every failure path so the next
> failure is diagnosable from the job output alone.

**Deploying to a host** is not wired up yet. When you add it:

- Push to a registry tagged with `${{ github.sha }}` — **never `latest` alone**, because you
  cannot roll back to "latest"
- Put secrets in **GitHub Actions Secrets**, never in the repo. A secret committed to git is
  compromised permanently — deleting it does not remove it from history
- Deploy only from `main`, only when green. Automating a deploy of broken code just gets you
  to the outage faster

Any container host works for this shape of app (Render, Fly.io, Railway, Cloud Run, App
Runner). **Free-tier terms change often and have not been verified here — check current
pricing before relying on one.**

---

## Limitations

**Read this before trusting it with anything real.**

| # | Limitation | Impact |
|---|---|---|
| 1 | **Never verified against the live FDA API.** Built from openFDA's published field docs; `api.fda.gov` was unreachable from the build environment. | Field names, date formats and the `status` vocabulary all need confirming on first live run. |
| 2 | **Brand mapping is a 20-entry hand-seeded list.** The real answer is RxNorm (NIH/NLM). | Unknown brands produce **false negatives** — the dangerous direction. Mitigated by the `/debug/normalize` endpoint. |
| 3 | **No authentication.** `userId` is passed in the clear and trusted. | Anyone can read anyone's list by guessing an ID. A medication list is sensitive health data. **Must be fixed before exposure.** |
| 4 | **In-memory storage.** | All data lost on restart. |
| 5 | ~~No scheduled sync or notifications~~ — **resolved in v0.2.** | Nightly sync, de-duplicated alerts and a manual trigger now exist. |
| 6 | **Sample fixture is hand-written**, not a real API capture. | Clearly labelled in the file itself. Replace with a real recording. |
| 7 | **Email delivery never tested against a real SMTP server.** | No mail relay reachable from the build environment. Defaults to `alert-channel=log`, which reports `delivered: false` rather than pretending. Check deliverability and spam placement on first live run — a shortage warning in a junk folder is a warning nobody receives. |
| 8 | **Alert ledger is in memory.** | A restart re-sends alerts already delivered. Goes away with limitation 4. |

### Roadmap

**v0.2 — delivered the alerting loop, and only that.** Worth being precise: the original
v0.2 plan listed three things and one of them shipped.

| Planned for v0.2 | Status |
|---|---|
| Nightly `@Scheduled` sync + email alerts | ✅ **done** |
| RxNorm integration | ❌ **not done** — moved to v0.3 |
| PostgreSQL via Spring Data JPA | ❌ **not done** — no Docker daemon in the build environment, so it could not be verified against real Postgres. Shipping JPA entities tested only against H2 would repeat the exact mistake documented in the CI section below: green locally for a reason that does not transfer. |

- **v0.3** — RxNorm (2); PostgreSQL, which also fixes the in-memory ledger (4, 8);
  authentication and encryption at rest (3)
- **v0.4** — SMS via Twilio; real recorded fixture (6)

**On SMS:** RefillRadar's users skew older, and email is the channel that group is least
reliably on. SMS would reach more of them — but it carries per-message cost and US A2P 10DLC
registration, which is procurement rather than code. Email first is the honest sequencing,
not a claim of parity.
- **Explicit non-goals** — suggesting alternative medicines (a clinical decision), pharmacy
  stock levels, anything that turns this into a regulated medical device

---

## Project layout

```
apps/refillradar/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/java/com/refillradar/
    │   ├── domain/       Medication, ShortageRecord, ShortageStatus, SupplyRisk, ShortageMatch
    │   ├── matching/     DrugNameNormalizer  <-- the hard part
    │   │                 ShortageMatcher     <-- the engine
    │   ├── refill/       RefillProjector     <-- date maths, injected Clock
    │   ├── shortage/     ShortageSource + fixture and openFDA implementations
    │   ├── alert/        AlertComposer       <-- the legal boundary lives in the wording
    │   ├── store/        MedicationRepository (in-memory for now)
    │   └── api/          REST controller and DTOs
    ├── main/resources/
    │   ├── application.yml
    │   └── fixtures/openfda-shortages-sample.json
    └── test/java/        105 tests, no network, no database
```

Every public class and method carries Javadoc explaining **why it exists**, not just what it
does — including the trade-offs that were considered and rejected.

## Sources

- [openFDA — Drug Shortages API](https://open.fda.gov/apis/drug/drugshortages) · [searchable fields](https://open.fda.gov/apis/drug/drugshortages/searchable-fields/)
- [FDA — Drug Shortages](https://www.fda.gov/drugs/drug-safety-and-availability/drug-shortages)
- [ASHP — Drug Shortages Statistics](https://www.ashp.org/drug-shortages/shortage-resources/drug-shortages-statistics)
- [Becker's — US drug shortages trending upward in 2026](https://www.beckershospitalreview.com/pharmacy/us-drug-shortages-trending-upward-in-2026/)
