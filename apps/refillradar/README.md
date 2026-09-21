# RefillRadar

**Personal early warning for drug shortages.** Watches the FDA shortage database against
*your* medication list and warns you weeks before a refill fails.

> **v0.2 — alerting loop closed.** 105 tests, runs offline. Not production ready: no auth,
> no database, openFDA never verified live. See [Limitations](#limitations).
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

JDK 21+, Maven 3.9+. No database, no network, no API key.

```bash
mvn test                # 105 tests, offline, ~10s
mvn spring-boot:run     # :8080
```

```bash
curl -X POST localhost:8080/api/medications -H 'Content-Type: application/json' \
  -d '{"userId":"robert","displayName":"Keppra 500mg","searchTerm":"Keppra",
       "lastFilledOn":"2026-09-01","daysSupply":24}'

curl -s localhost:8080/api/users/robert/supply-check | python3 -m json.tool
```

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

| Method | Path | |
|---|---|---|
| `POST` | `/api/medications` | Register a medication |
| `GET` | `/api/users/{id}/medications` | List |
| `DELETE` | `/api/medications/{id}` | Remove |
| `GET` | `/api/users/{id}/supply-check` | **Main endpoint** |
| `GET` | `/api/debug/normalize?name=X` | How a drug name was interpreted |
| `POST` | `/api/admin/sync` | Run the nightly sync now |
| `GET` | `/api/sync/status` | Age of the cached feed |
| `POST`/`GET` | `/api/users/{id}/contact` | Where alerts go / is the user reachable |

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

```bash
mvn package && java -jar target/refillradar-0.1.0-SNAPSHOT.jar
java -jar target/*.jar --refillradar.shortage-source=openfda   # live API

docker build -t refillradar . && docker run -p 8080:8080 refillradar
```

Multi-stage build, JRE-only runtime stage, non-root user.

**CI** ([`refillradar-ci.yml`](../../.github/workflows/refillradar-ci.yml)): build + test →
upload reports (`if: always()`) → build container (`needs: build`) → smoke test that starts
the container and curls it.

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
| 3 | **No authentication.** `userId` is trusted in the clear | Anyone can read anyone's list. **Must fix before exposure** |
| 4 | **In-memory storage** | All data lost on restart |
| 5 | ~~No scheduled sync~~ — **resolved in v0.2** | |
| 6 | **Fixture is hand-written**, not a real capture | Labelled in the file itself |
| 7 | **Email never tested against real SMTP** | Defaults to `alert-channel=log`, which reports `delivered: false` rather than pretending. Check spam placement on first live run |
| 8 | **Alert ledger is in memory** | A restart re-sends delivered alerts. Goes away with #4 |

### Roadmap

v0.2 planned three things; **one shipped**. Nightly sync ✅. RxNorm ❌. PostgreSQL ❌ —
deliberately: no Docker daemon here, and shipping JPA entities tested only against H2 would
repeat CI bug #2 above, green locally for a reason that doesn't transfer.

- **v0.3** — RxNorm (2); PostgreSQL, which also fixes the ledger (4, 8); auth (3)
- **v0.4** — SMS via Twilio; real recorded fixture (6)

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
