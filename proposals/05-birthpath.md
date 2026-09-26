# 5. BirthPath — navigating a maternity care desert

> **One line:** For the 5.8 million women living in counties without full maternity care —
> where can you actually give birth, how far is it really, and what is the plan if labour
> starts at 2am in February?

**Difficulty:** Hardest of the five · **Primary language:** Python + TypeScript
**Build order recommendation:** Last. Build the other four first.

---

## The problem

This is the most severe problem in this document, and the least served by software.

- **One in three US counties are maternity care deserts** in 2026. *(Verified ×3 — March of
  Dimes, via TIME and Contemporary OB/GYN.)*
- **5.8 million women and 358,000 infants live in counties without full access to maternity
  care.** *(Verified ×2)*
- **At least 96 labour and delivery unit closures since January 2024** — and **nearly 60% of
  those eliminated the county's only birthing facility.** *(Verified ×2)*
- **146 rural hospitals** have stopped delivering babies or announced they will stop before
  the end of 2026 — a **14% reduction in rural labour and delivery units** since the end of
  2020. *(Single source)*
- **718 hospitals stopped providing obstetric care between 2010 and 2024.** *(Single source)*
- **By 2024, 57.5% of rural hospitals did not provide obstetric care**, versus 43.6% of urban
  hospitals. **Nearly 58% of rural counties lack obstetric clinicians.** *(Verified ×2)*
- **More than half of all US counties lack a hospital with labour and delivery services**,
  affecting nearly **370,000 births annually**. *(Single source)*

### The distance number is the one that matters

> **US average distance to obstetric care: 8.1 miles. For women in maternity care deserts:
> 28.1 miles.** Closures added an average of **25 minutes** of travel time in affected
> communities. *(Verified ×2)*

Regionally it is starker: **76 of 107 counties in west Texas are maternal healthcare
deserts.** *(Single source)*

Twenty-five extra minutes sounds survivable until you attach it to a postpartum haemorrhage,
a placental abruption, or a precipitous labour on an icy road at 2am. **This is a problem
made of geography and time**, which is exactly what makes it tractable to software — and
exactly what makes getting it wrong so serious.

---

## Competitive landscape

I searched and **found no consumer-facing app addressing maternity care desert logistics.**
There is substantial research (March of Dimes' annual report, peer-reviewed work on west
Texas and Missouri) and there is journalism. There are general pregnancy apps — week-by-week
trackers, contraction timers — but those assume the hospital is a solved variable.

**⚠️ Honesty flag:** absence of search results is weak evidence. It may mean nobody has built
it; it may mean something exists that I did not surface, or that hospital systems provide
this regionally. **Verify properly before committing months to this.** This is the least
certain competitive claim in the document, and I would rather say so than let you assume I
proved a negative.

That said, the *reason* nobody may have built it is instructive: the affected population is
rural, lower-income, and dispersed. It is a **bad venture market and an excellent public-good
project** — and those are not the same thing.

---

## The wedge

Not a pregnancy tracker. A **logistics and contingency planner.**

1. **Where can you actually deliver?** Not "nearest hospital" — *nearest hospital with an open
   labour and delivery unit*, which is a different and rapidly changing set. Given the closure
   rate, general mapping tools are frequently wrong.
2. **Real travel time, not straight-line distance.** Mileage is nearly useless rurally. What
   matters is drive time, by road, **at 2am, in winter, possibly in bad weather.** Two
   facilities 30 miles away can be 35 minutes and 80 minutes apart in practice.
3. **The written plan.** A shareable page: primary facility, backup facility, route, drive
   time, who drives, who takes the other children, when to leave. Rural obstetric guidance
   often involves relocating closer to a facility near the due date — an enormous logistical
   and financial undertaking that people currently plan on paper.
4. **Prenatal visit clustering.** If appointments are a 56-mile round trip, combining
   appointments is not convenience, it is the difference between attending and not.

---

## MVP scope (v0.1)

**In:** one state; a verified dataset of facilities with active L&D units; enter your address
→ ranked facilities with real drive times; a printable/shareable birth logistics plan.

**Out:** clinical guidance of any kind; risk scoring; telehealth; appointment booking.

**The bright line, and it is absolute:** BirthPath gives **logistics, never medical advice.**
It says *"this hospital is 47 minutes away by road."* It must **never** say *"you have time"*
or *"this is safe"* or imply a risk assessment. Distance is a fact. Whether that distance is
acceptable for a specific pregnancy is a clinical judgement belonging to a clinician.

---

## Data sources

Harder than the other four — this is a real part of the difficulty:

- **CMS Provider of Services file** — facility-level data including services offered.
- **HRSA data portal** — health professional shortage areas, facility locations.
- **March of Dimes Maternity Care Deserts report** — the authoritative county classification;
  good for validating your own pipeline.
- **OpenStreetMap + OSRM / Valhalla** — open-source routing engines. Self-hosting avoids
  Google Maps API costs, which matter at scale.
- **State hospital licensing databases** — often the most current source for closures.

**⚠️ The core data risk:** L&D closures happen faster than federal datasets refresh. A
hospital can announce closure and stop deliveries months before any national file reflects it.
**Stale data here is not a cosmetic bug — it could send someone in labour to a closed unit.**

**The mitigation is not technical.** It is a verification process: a human confirms each
facility's L&D status on a schedule (quarterly minimum), every record displays a "verified on"
date, and users can report a discrepancy in one tap. **Accept the unglamorous manual step.**
Automating it is the wrong instinct here, and recognising when *not* to automate is a genuine
engineering skill.

---

## Stack, and why

**Backend: Python + Django + PostgreSQL with PostGIS.**

*Why Python here, and not Java:* this project is 70% data wrangling — reconciling federal
files, state licensing records and OSM extracts, all with inconsistent identifiers and
formats. Python's data ecosystem (pandas, GeoPandas, Shapely) is materially better for that
work, and the early phase is exploratory: load it, look at it, find out how wrong it is,
adjust. That iteration loop is where Python wins decisively.

*Why PostGIS specifically:* plain PostgreSQL can store a latitude and longitude, but it has
no idea what they *mean*. PostGIS adds real geographic types and indexes, so "find every
facility within 60 minutes' drive" is one indexed query.

*Analogy:* plain Postgres storing coordinates is **a filing cabinet holding a map** — the map
is in there, but the cabinet cannot read it; you must pull it out and measure by hand. PostGIS
is **a filing cabinet that understands geography** — you ask "what is near this point?" and
it answers directly. On a project whose every question is a distance question, that
difference is the whole architecture.

*Routing:* use a real routing engine (OSRM or Valhalla over OSM data), never straight-line
distance. In rural areas with rivers, mountains and sparse road networks, straight-line
distance is not merely imprecise — **it is systematically wrong in the dangerous direction**,
always understating travel time.

**Frontend: TypeScript + a mapping library** (MapLibre GL, which is open source, over Mapbox,
which bills per load).

**Offline matters again:** rural broadband and cell coverage are unreliable, so the birth plan
must be **printable on paper**. A PDF that works when the phone has no bars beats an elegant
app that does not. Design for the worst moment, not the demo.

---

## Deployment

### Local

```bash
# Prerequisites: Python 3.12, Docker, ~8GB free disk for OSM extracts
git clone <repo> && cd birthpath

python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

docker compose up -d db osrm     # PostGIS + routing engine

cp .env.example .env
python manage.py migrate
python manage.py import_facilities --state=TX   # ETL from CMS/HRSA
python manage.py runserver
```

The `import_facilities` management command is the heart of the project. Write it as an
**idempotent** job — safe to run repeatedly, producing the same result. Data pipelines get
re-run constantly, usually after a partial failure, and a pipeline that duplicates rows on a
second run will quietly poison the dataset.

### Cloud CI/CD — GitHub Actions

```yaml
name: CI
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgis:
        image: postgis/postgis:16-3.4
        env: { POSTGRES_PASSWORD: postgres }
        options: >-
          --health-cmd pg_isready --health-interval 10s
          --health-timeout 5s --health-retries 5
        ports: ['5432:5432']
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.12', cache: 'pip' }
      - run: pip install -r requirements.txt
      - run: python manage.py test
      - run: python manage.py validate_facility_data --strict
```

That last step is unusual and deserves explanation: it is a **CI check on data quality**, not
code. It should fail the build if any facility record lacks coordinates, has a verification
date older than 90 days, or claims an L&D unit without a source citation. **On a data-driven
safety app, bad data is a bug of exactly the same severity as bad code — so gate it the same way.**

**Deployment note:** self-hosted routing needs meaningfully more RAM than a typical small
container instance, and OSM extracts are large. This app costs real money to run, unlike the
others. **I have not verified current hosting prices — size it properly before committing.**

---

## Risks

1. **🔴 Stale data could cause direct physical harm.** The single greatest risk in this entire
   document. Mitigations: visible "verified on" dates, scheduled human re-verification, one-tap
   discrepancy reporting, and prominent guidance to **call ahead before travelling**.
2. **🔴 Liability.** Anything touching childbirth logistics carries real exposure. **Get legal
   advice before public launch** — this is not a risk to reason your way through alone.
3. **🟠 The users have the worst connectivity.** Rural broadband gaps are correlated with
   exactly the counties you serve. Mitigations: lightweight pages, offline-capable, printable.
4. **🟠 Routing accuracy rurally.** OSM coverage is thinner in rural areas; seasonal road
   closures and unpaved roads are often unmodelled. Mitigation: state a margin of error
   explicitly; never present a drive time as precise.
5. **🟡 Funding.** No plausible consumer revenue. Realistic paths: public health grants, state
   maternal health programmes, hospital systems, rural health foundations, March of Dimes-type
   organisations. **Treat this as civic infrastructure, not a startup.**
6. **🟡 Scope creep toward clinical features.** Users *will* ask "is this far too far?" Holding
   the logistics-only line under that pressure is the ongoing discipline of this project.

---

## Why build it anyway

Every risk above is real, and I have not softened any of them. But note what this problem is
made of: **facility locations, road networks, and time.** Those are solvable with open data
and a routing engine. The hard parts are verification discipline and restraint — not
algorithms.

It is last on the build order for good reason. But of the five, it is the one where a working
version most clearly makes someone's worst night measurably less dangerous. That is worth
building toward, once the other four have taught you the craft.

## Sources
- [March of Dimes — Nowhere to Go: Maternity Care Deserts Across the U.S.](https://www.marchofdimes.org/maternity-care-deserts-report)
- [TIME — More Than One Third of U.S. Counties Are Maternity Care Deserts](https://time.com/article/2026/08/11/maternity-care-deserts-u.s.-report/)
- [Contemporary OB/GYN — March of Dimes 2026 report coverage](https://www.contemporaryobgyn.net/view/march-of-dimes-2026-maternity-care-deserts-report-closures-access)
- [Center for Healthcare Quality & Payment Reform — Stopping the Loss of Rural Maternity Care](https://ruralhospitals.chqpr.org/Maternity_Care.html)
- [PMC — Maternity Care Deserts: Key Drivers of the National Maternal Health Crisis](https://pmc.ncbi.nlm.nih.gov/articles/PMC12096371/)
- [PMC — Maternal healthcare deserts in West Texas](https://pmc.ncbi.nlm.nih.gov/articles/PMC13087663/)
- [KCUR — Rural Missouri families face riskier births as maternity wards vanish](https://www.kcur.org/health/2026-09-17/missouri-maternity-ward-birth-access-rural-health-care)
