# BirthPath

**Navigating a maternity care desert.** For the 5.8 million women in counties without full
maternity care: where can you actually give birth, how far is it *really*, and what's the
plan if labour starts at 2am in February.

> **v0.1 — working core engine.** 65 tests, runs offline. Not production ready: the facility
> data is **illustrative sample data, not a real directory**.
>
> **Travel logistics only — never medical advice.** It says *"this hospital is 47 minutes
> away."* It will never say *"you have time"* or *"this is safe."*

Research: [`../../proposals/05-birthpath.md`](../../proposals/05-birthpath.md)

## Why

- **One in three US counties are maternity care deserts** in 2026.
- **5.8 million women and 358,000 infants** live in counties without full access.
- **At least 96 labour and delivery closures since January 2024** — nearly **60%** eliminated
  their county's *only* birthing facility.
- **146 rural hospitals** stopped delivering or announced they will before end-2026; **718
  hospitals** stopped between 2010 and 2024. By 2024, **57.5% of rural hospitals** had no
  obstetric care.

> **US average distance to obstetric care: 8.1 miles. In a maternity care desert: 28.1
> miles.** Closures added an average of **25 minutes** of travel time.

Twenty-five extra minutes sounds survivable until you attach it to a postpartum haemorrhage
or a precipitous labour on an icy road at 2am. The problem is made of **facility locations,
road networks and time** — which is what makes it tractable, and what makes getting it wrong
so serious.

## Quick start

Python 3.11+. No database, no network, no API key. Port **8084**.

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest -q                                    # 65 tests
.venv/bin/python -m uvicorn birthpath.main:app --app-dir src --port 8084
```

```bash
curl -s "localhost:8084/api/plan?latitude=32.0&longitude=-102.1&on=2026-09-20"
```
```
PRIMARY: Midland Regional Medical Center   roughly 2-4 minutes   [estimated]
BACKUP : Odessa County Hospital            roughly 27-53 minutes  call ahead=True

WARNINGS:
  * CALL AHEAD before you travel, every time...
  * BirthPath gives travel logistics only. It does NOT give medical advice...
  * Print this plan or write it down. Mobile coverage is unreliable...
```

### The part that matters most

```bash
curl -s "localhost:8084/api/facilities?on=2026-09-20"
```
```
FACILITY                            RECORDED   EFFECTIVE  FRESHNESS        DOWNGRADED
Midland Regional Medical Center     delivers   delivers   fresh
Odessa County Hospital              delivers   delivers   ageing
Big Spring Community Hospital       delivers   unknown    stale             <-- YES
Pecos Valley Hospital               delivers   unknown    never_verified    <-- YES
Monahans District Hospital          closed     closed     fresh
```

**Big Spring's record says it delivers babies. BirthPath refuses to say so**, because it was
last confirmed in January 2025 and L&D units have been closing fast.

## Stale data is the real danger

The greatest risk isn't a routing bug — it's **a facility listed as delivering that stopped
six months ago.** Sending someone in labour to a closed unit is a physical harm, and it's
entirely possible because closures outpace dataset refreshes.

| Freshness | Meaning | Effect |
|---|---|---|
| `fresh` | Confirmed ≤ 90 days | Shown as confirmed |
| `ageing` | Confirmed ≤ 180 days | Confirmed, with "worth a quick call" |
| `stale` | Older | **Downgraded to `unknown`** |
| `never_verified` | Imported, never confirmed | **Downgraded to `unknown`** |

`Facility.effective_status()` is the most important method here. Without it, a record
confirmed in 2024 presents in 2026 as settled fact.

**The mitigation isn't technical — it's process.** A human re-verifies on a schedule, every
record shows its date, users can report discrepancies. Automating that away is the wrong
instinct, and recognising when *not* to automate is a real engineering skill.

**Unconfirmed facilities are surfaced, not hidden.** They may be the best option — we just
can't say so, and one phone call the user can make (and we can't) would settle it.

## Straight-line distance is wrong in the dangerous direction

Great-circle distance ignores rivers, mountains and sparse rural grids. Its error isn't
random — roads are **always** at least as long, so an unadjusted figure *always understates*
time in a car.

So `haversine_miles` only decides which facilities are worth considering. Travel time is
separate and explicit about guessing:

- **`RoadFactorEstimator`** — 1.4× rural circuity factor, stated speed assumptions. Always
  available.
- **`OsrmEstimator`** — real road routing. Correct, and **never run against a live server**.

Both return a **range, never a point** (`"roughly 27-53 minutes"`) with a `confidence` field
and a plain-language `basis`. A single confident number invites planning the data can't
support, and here over-confidence has a body count.

`OsrmEstimator` **raises rather than silently falling back** — a caller that thinks it got a
routed answer when it got a guess is exactly the failure this structure prevents.

## API

| Method | Path | |
|---|---|---|
| `GET` | `/api/plan?latitude=&longitude=&on=` | **Main endpoint** |
| `GET` | `/api/facilities?on=` | Every record with its verification state |
| `GET` | `/api/health` | Liveness + freshness thresholds |

`/api/facilities` exists so the freshness model is **inspectable** — a user, journalist or
health department should see exactly what we are and aren't vouching for.

## Deployment

```bash
.venv/bin/python -m uvicorn birthpath.main:app --app-dir src --port 8084 --reload
docker build -t birthpath . && docker run -p 8084:8084 birthpath
```

Multi-stage build — dependencies into a venv in their own stage, so the runtime image carries
no pip cache or toolchain. Non-root user.

**CI** ([`birthpath-ci.yml`](../../.github/workflows/birthpath-ci.yml)): pytest →
`pip-audit` (advisory, `continue-on-error`) → container build (`needs: build`, not
branch-gated) → **smoke test asserting the safety invariant** via
[`ci/assert_stale_downgraded.py`](ci/assert_stale_downgraded.py).

That last check was verified **both ways**: it passes on real output, and fails when the
response is tampered with to make the stale record look confirmed. *A safety check nobody has
seen fail is a safety check nobody should trust.*

## Limitations

| # | Limitation | Impact |
|---|---|---|
| 1 | **Facility data is illustrative.** Real towns, invented facilities | Labelled in every response: *"Never use this to plan a real birth."* Real data needs CMS Provider of Services + HRSA + state licensing |
| 2 | **No real routing.** `OsrmEstimator` never run live | The fallback estimator is used, and says so |
| 3 | **No PostGIS.** In-process haversine | Fine at fixture scale; a real dataset wants a spatial index |
| 4 | **Re-verification is manual and unbuilt** | The scheduled human process is the real cost. No software substitute |
| 5 | **No printable output** | The plan says "print this" but serves only JSON. Rural coverage is worst exactly where this matters |
| 6 | **No user discrepancy reporting** | One-way. Users are the fastest closure signal there is |

### Roadmap

- **v0.2** — real ETL from CMS/HRSA (1); PostGIS (3); printable PDF (5)
- **v0.3** — self-hosted OSRM (2); discrepancy reporting (6); re-verification tooling (4)

**Non-goals** — clinical guidance; risk scoring; telehealth; appointment booking.

### Two risks no code fixes

**Liability.** Anything touching childbirth logistics carries real exposure. **Get legal
advice before public launch.**

**Funding.** No plausible consumer revenue — the population is rural, lower income and
dispersed. A bad venture market and an excellent public-good project are not the same thing.
Realistic paths: public health grants, state maternal health programmes, hospital systems.
**Treat this as civic infrastructure, not a startup.**

## Why Python here

The other four apps are Java. This is **70% data wrangling and geospatial work** — federal
files, state licensing records and road networks with inconsistent identifiers — where
Python's iteration loop wins decisively.

**A deviation worth flagging:** the proposal specified Django + PostGIS. The *Python* half
holds completely. The *Django* half was justified by its admin, auth and ORM — all of which
need a database, and there's no Docker daemon here, so PostGIS can't run. A Django app
configured against a database nobody can start is a worse artefact than a working one
without it. v0.1 is **FastAPI with in-memory fixtures**.

## Sources

[March of Dimes — Nowhere to Go](https://www.marchofdimes.org/maternity-care-deserts-report) ·
[TIME — 1 in 3 counties](https://time.com/article/2026/08/11/maternity-care-deserts-u.s.-report/) ·
[Contemporary OB/GYN](https://www.contemporaryobgyn.net/view/march-of-dimes-2026-maternity-care-deserts-report-closures-access) ·
[CHQPR — Rural Maternity Care](https://ruralhospitals.chqpr.org/Maternity_Care.html) ·
[PMC — West Texas deserts](https://pmc.ncbi.nlm.nih.gov/articles/PMC13087663/) ·
[KCUR — Rural Missouri](https://www.kcur.org/health/2026-09-17/missouri-maternity-ward-birth-access-rural-health-care)
