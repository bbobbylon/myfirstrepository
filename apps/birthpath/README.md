# BirthPath

**Navigating a maternity care desert.** For the 5.8 million women living in counties without
full maternity care: where can you actually give birth, how far is it *really*, and what's the
plan if labour starts at 2am in February.

> **Status: v0.1 — working core engine.** 65 tests passing. Runs offline, no database.
> Not production ready: the facility data is **illustrative sample data, not a real
> directory**. See [Limitations](#limitations).
>
> **Travel logistics only — never medical advice.** BirthPath says *"this hospital is 47
> minutes away."* It will never say *"you have time"* or *"this is safe."* Distance is a fact;
> whether a distance is acceptable for a specific pregnancy is a clinical judgement.

Problem research: [`../../proposals/05-birthpath.md`](../../proposals/05-birthpath.md)

---

## The problem

- **One in three US counties are maternity care deserts** in 2026.
- **5.8 million women and 358,000 infants** live in counties without full access.
- **At least 96 labour and delivery closures since January 2024** — nearly **60%** eliminated
  their county's *only* birthing facility.
- **146 rural hospitals** stopped delivering or announced they will before end-2026 — a **14%**
  reduction in rural L&D units since 2020. **718 hospitals** stopped between 2010 and 2024.
- By 2024, **57.5% of rural hospitals** did not provide obstetric care.

### The number that matters

> **US average distance to obstetric care: 8.1 miles. In a maternity care desert: 28.1 miles.**
> Closures added an average of **25 minutes** of travel time.

Twenty-five extra minutes sounds survivable until you attach it to a postpartum haemorrhage,
a placental abruption, or a precipitous labour on an icy road at 2am.

This problem is made of **facility locations, road networks and time** — which is exactly what
makes it tractable to software, and exactly what makes getting it wrong so serious.

---

## Quick start

**Prerequisites:** Python 3.11+. No database, no network, no API key.

```bash
cd myfirstrepository/apps/birthpath
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest -q                                    # 65 tests
.venv/bin/python -m uvicorn birthpath.main:app --app-dir src --port 8084
```

Port **8084** — all five apps run side by side.

### Try it

```bash
curl -s "localhost:8084/api/plan?latitude=32.0&longitude=-102.1&on=2026-09-20" | python3 -m json.tool
```
```
PRIMARY: Midland Regional Medical Center (Midland, TX)
        roughly 2-4 minutes  ~1.8 road miles  [estimated]
BACKUP : Odessa County Hospital (Odessa, TX)
        roughly 27-53 minutes  ~26.6 road miles  [estimated]   call ahead=True

WARNINGS:
  * CALL AHEAD before you travel, every time. Labour and delivery units have been closing...
  * BirthPath gives travel logistics only. It does NOT give medical advice...
  * Print this plan or write it down. Mobile coverage is unreliable in exactly the places...
```

### Now the part that matters most

```bash
curl -s "localhost:8084/api/facilities?on=2026-09-20"
```
```
FACILITY                            RECORDED   EFFECTIVE  FRESHNESS        DOWNGRADED
-------------------------------------------------------------------------------------
Midland Regional Medical Center     delivers   delivers   fresh
Odessa County Hospital              delivers   delivers   ageing
Big Spring Community Hospital       delivers   unknown    stale             <-- YES
Pecos Valley Hospital               delivers   unknown    never_verified    <-- YES
Monahans District Hospital          closed     closed     fresh
```

**Big Spring's record says it delivers babies. BirthPath refuses to say so**, because it was
last confirmed in January 2025 and L&D units have been closing fast.

---

## The design centre: stale data is the real danger

The greatest risk here isn't a routing bug. It's **a facility listed as delivering that
stopped six months ago.** Sending someone in labour to a closed unit is a physical harm, and
it's entirely possible because closures outpace dataset refreshes.

So verification is a **first-class property of every record**, not a footnote:

| Freshness | Meaning | Effect |
|---|---|---|
| `fresh` | Confirmed ≤ 90 days | Shown as confirmed |
| `ageing` | Confirmed ≤ 180 days | Confirmed, with "worth a quick call" |
| `stale` | Confirmed longer ago | **Downgraded to `unknown`** |
| `never_verified` | Imported, never confirmed | **Downgraded to `unknown`** |

`Facility.effective_status()` is the most important method in the app. Without it, a record
confirmed in 2024 presents in 2026 as settled fact.

**The mitigation isn't technical — it's process.** A human re-verifies on a schedule, every
record shows its "verified on" date, and users can report a discrepancy. Automating that away
is the wrong instinct, and recognising when *not* to automate is a real engineering skill.

**Unconfirmed facilities are surfaced, not hidden.** They may well be the best option — we
just can't say so, and one phone call the user can make (and we can't) would settle it.

---

## Straight-line distance is wrong in the dangerous direction

Great-circle distance ignores rivers, mountains and sparse rural road grids. Its error isn't
random — roads are **always** at least as long as the straight line, so an unadjusted figure
*always understates* time in a car.

So `haversine_miles` exists only to decide which facilities are worth considering.
Turning distance into time is a separate module that's explicit about guessing:

- **`RoadFactorEstimator`** — applies a 1.4× rural circuity factor and stated speed
  assumptions. Always available, no dependencies.
- **`OsrmEstimator`** — real road routing. Correct, and **never run against a live server**.

Both return a **range, never a point** (`"roughly 27-53 minutes"`), with a `confidence` field
and a plain-language `basis`. A single confident number invites planning the data can't
support, and on this problem over-confidence has a body count.

`OsrmEstimator` **raises rather than silently falling back** — a caller that thinks it got a
routed answer when it got a guess is exactly the failure this structure prevents.

---

## API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/plan?latitude=&longitude=&on=` | **Main endpoint** — ranked plan with warnings |
| `GET` | `/api/facilities?on=` | Every record with its verification state and downgrades |
| `GET` | `/api/health` | Liveness + the freshness thresholds in force |

`/api/facilities` exists so the freshness model is **inspectable**. A user — or a journalist,
or a health department — should be able to see exactly what we are and aren't vouching for,
rather than taking the ranking on faith.

---

## Deployment

### Local

```bash
.venv/bin/python -m pytest -q
.venv/bin/python -m uvicorn birthpath.main:app --app-dir src --port 8084 --reload
```

### Docker

```bash
docker build -t birthpath . && docker run -p 8084:8084 birthpath
```

Multi-stage build — dependencies install into a venv in their own stage so the runtime image
carries no pip cache or build toolchain. Runs as a **non-root user**.

### Cloud CI/CD — GitHub Actions

[`.github/workflows/birthpath-ci.yml`](../../.github/workflows/birthpath-ci.yml):

1. **Build and test** — Python 3.11, `pytest`, pip cache keyed on `requirements-dev.txt`
2. **Dependency audit** — `pip-audit`, `continue-on-error` so a new CVE in a transitive
   dependency is *visible* without turning an unrelated PR red
3. **Build container** — `needs: build`, **not branch-gated**
4. **Smoke test — asserts the safety invariant against a running container**

Step 4 runs [`ci/assert_stale_downgraded.py`](ci/assert_stale_downgraded.py), which fails the
build unless the stale record resolves to `unknown`. It's a script rather than inline YAML so
it can be run locally — and it was verified **both ways**: it passes on real output, and fails
when the response is tampered with to make the stale record look confirmed. A safety check
nobody has seen fail is a safety check nobody should trust.

---

## Limitations

| # | Limitation | Impact |
|---|---|---|
| 1 | **Facility data is illustrative, not real.** Coordinates are real west Texas towns; the facilities and statuses are invented. | Labelled unmissably in every response: *"Never use this to plan a real birth."* Real data needs CMS Provider of Services + HRSA + state licensing. |
| 2 | **No real routing.** `OsrmEstimator` never ran against a live server — no OSRM instance reachable, and no Docker daemon here. | The fallback estimator is used, and says so in every response. |
| 3 | **No PostGIS.** Distance is in-process haversine. | Fine at fixture scale; a real dataset of thousands of facilities wants a spatial index. **v0.2.** |
| 4 | **Re-verification is manual and unbuilt.** | The scheduled human process is the product's real cost. No software substitute. |
| 5 | **No printable output yet.** | The plan says "print this" but only serves JSON. A PDF matters: rural coverage is worst exactly where this matters most. |
| 6 | **No user reporting of discrepancies.** | Currently one-way. Users are the fastest closure signal there is. |

### Roadmap

- **v0.2** — real facility ETL from CMS/HRSA (1); PostGIS spatial index (3); printable PDF (5)
- **v0.3** — self-hosted OSRM routing (2); user discrepancy reporting (6); re-verification
  workflow tooling (4)
- **Explicit non-goals** — clinical guidance of any kind; risk scoring; telehealth; appointment
  booking

### Two risks no amount of code fixes

**Liability.** Anything touching childbirth logistics carries real exposure. **Get legal
advice before public launch** — that's not a risk to reason through alone.

**Funding.** There's no plausible consumer revenue: the affected population is rural, lower
income and dispersed. That's a bad venture market and an excellent public-good project, and
those aren't the same thing. Realistic paths are public health grants, state maternal health
programmes, hospital systems, or March of Dimes-type organisations. **Treat this as civic
infrastructure, not a startup.**

---

## Why Python here

The other four apps are Java. This one isn't, and the reason is substantive rather than
variety for its own sake: this is **70% data wrangling and geospatial work** — reconciling
federal files, state licensing records and road networks with inconsistent identifiers. That
iteration loop is where Python wins decisively.

**A deviation worth flagging:** the proposal specified Django + PostGIS. The *Python* half
holds completely. The *Django* half was justified by its admin, auth and ORM — all of which
need a database, and this environment has no Docker daemon, so PostGIS can't run. A Django app
configured against a database nobody can start would be a worse artefact than a working one
without it. So v0.1 is **FastAPI with in-memory fixtures**: it runs anywhere Python does, and
the geospatial logic — the part that actually needed Python — is fully exercised.

## Sources

- [March of Dimes — Nowhere to Go: Maternity Care Deserts](https://www.marchofdimes.org/maternity-care-deserts-report)
- [TIME — More Than One Third of U.S. Counties Are Maternity Care Deserts](https://time.com/article/2026/08/11/maternity-care-deserts-u.s.-report/)
- [Contemporary OB/GYN — March of Dimes 2026 report coverage](https://www.contemporaryobgyn.net/view/march-of-dimes-2026-maternity-care-deserts-report-closures-access)
- [CHQPR — Stopping the Loss of Rural Maternity Care](https://ruralhospitals.chqpr.org/Maternity_Care.html)
- [PMC — Maternal healthcare deserts in West Texas](https://pmc.ncbi.nlm.nih.gov/articles/PMC13087663/)
- [KCUR — Rural Missouri families face riskier births](https://www.kcur.org/health/2026-09-17/missouri-maternity-ward-birth-access-rural-health-care)
