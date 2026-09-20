# ShadeClock

**Heat safety scheduling for outdoor work crews.** Turns a weather forecast into a work/rest
timetable, applies the heat rules of the state you're standing in, and tracks which workers
aren't yet heat-adapted.

> **Status: v0.1 — working core engine.** 80 tests passing. Runs offline. Not production
> ready: the state thresholds are **unverified against regulation text**, there's no auth, and
> no database. See [Limitations](#limitations).
>
> **Decision support, not a compliance guarantee.** ShadeClock does not determine legal
> compliance. Verify every threshold against the regulation before any crew relies on it.

Problem research and competitive analysis: [`../../proposals/01-shadeclock.md`](../../proposals/01-shadeclock.md)

---

## The problem

- A **George Washington University / Harvard** study in *Environmental Health*, analysing OSHA
  injury data across 48 states, found **~28,000 injuries per year linked to work on hot days**.
- Risk starts climbing at a **heat index around 85°F** and rises further **at 90°F+**.
- Crucially: **"Workers in states with OSHA workplace heat exposure standards appear to have a
  lower risk of injury on hot days."** Codified rules measurably help — that sentence is the
  entire thesis of this app.

**But the rules are a patchwork.** As of 2026 there is **no finalised federal heat standard** —
OSHA proposed one in August 2024 and it stalled after the January 2025 regulatory freeze. In its
place: a Heat National Emphasis Program, plus roughly **7 states** with their own enforceable
standards (CA, CO, MD, MN, NV, OR, WA). A contractor working three states faces three rulebooks
and no federal floor.

### Why the official app doesn't close the gap

There *is* an official **OSHA-NIOSH Heat Safety Tool**. CDC's own page states it **"is not
receiving updates at this time."** User reviews report it reads **7–10°F cooler than actual**,
shows *forecast* rather than current conditions, and — most tellingly — **has no break
scheduling**, the one output that's actually actionable.

| | OSHA-NIOSH Tool | **ShadeClock** |
|---|---|---|
| Audience | One worker | **A supervisor + their crew** |
| Output | A risk colour | **A timetable with break times** |
| Rules | Generic advice | **The rule for the state you're in** |
| Acclimatisation | Mentioned | **Tracked per worker, per day** |
| Maintenance | Stalled | Actively maintained |

---

## Quick start

**Prerequisites:** JDK 21+, Maven 3.9+. No database. No network. No API key.

```bash
git clone https://github.com/bbobbylon/myfirstrepository.git
cd myfirstrepository/apps/shadeclock

mvn test              # 80 tests, fully offline, ~10 seconds
mvn spring-boot:run   # starts on :8081
```

Port **8081**, so it runs alongside RefillRadar (`:8080`).

### Try it

```bash
# Create a crew: a veteran, a new starter, and a veteran back from leave
CREW=$(curl -sS -X POST localhost:8081/api/crews -H 'Content-Type: application/json' -d '{
  "name":"Paving Crew","siteLabel":"Route 12 resurfacing",
  "latitude":34.0522,"longitude":-118.2437,"jurisdiction":"CA",
  "workers":[
    {"name":"Maria (10yr veteran)","heatWorkStartedOn":"2016-04-01"},
    {"name":"Dev (started yesterday)","heatWorkStartedOn":"2026-07-14"},
    {"name":"Sam (back from 2wk leave)","heatWorkStartedOn":"2020-01-01",
     "lastAbsenceEndedOn":"2026-07-15"}
  ]}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -s "localhost:8081/api/crews/$CREW/schedule?date=2026-07-15" | python3 -m json.tool
```

Real output from that sequence:

```
CREW     : Paving Crew @ Route 12 resurfacing
RULES    : California
PEAK     : 110F at 2026-07-15T14:00

WATCH THESE PEOPLE:
  - Dev (started yesterday)
  - Sam (back from 2wk leave)

TIMETABLE ( 7 breaks, 90 min total rest ):
  11:50-12:00   98F   10 minutes rest in shade
  12:50-13:00  104F   ...
  13:45-14:00  107F   15 minutes rest in shade   <- cadence steps up past 105F
  14:45-15:00  110F
  ...
```

**Look at who got flagged.** Maria is a ten-year veteran and is *not* flagged. Sam is *also* a
veteran — and *is* flagged, because he's back from two weeks off and his heat adaptation is
gone. That's the case a foreman juggling twelve people forgets, and it's why this is software
rather than a laminated card.

---

## How it works

```
   Forecast (hourly °F + RH)
            |
   HeatIndexCalculator          NWS Rothfusz regression, + dry/humid corrections
            |
   HourlyConditions             heat index + NWS risk band, derived together
            |
   HeatRuleset (by state)       rules as DATA, with citations + verification status
            |
   ScheduleBuilder  <---- Crew (workers + their acclimatisation dates)
            |
   WorkRestSchedule             timetable + named at-risk workers + caveats
```

### The heat index is implemented properly, and validated

The full NWS algorithm: simple formula below 80°F apparent, Rothfusz regression above, plus
both correction bands (subtract in very dry heat, add in humid moderate heat).

**Validated against the published NWS chart**: 13 of 15 independently-sourced reference points
landed within **0.8°F** — inside the regression's stated **±1.3°F** error. During validation two
assumed reference values turned out to be wrong rather than the code (104°F/35% is 112°F on the
chart, not the 109°F first assumed).

Two limits are carried in the code as constants, not buried in comments:

- **`STATED_ERROR_F = 1.3`** — so nothing renders false precision like "91.4732°F"
- **`FULL_SUN_ADDITION_F = 15.0`** — NWS states full sunshine can add **up to 15°F**, and the
  chart applies to *shade*. A crew reading a "safe" 88°F may really be at 103°F, the Danger
  band. This is why ShadeClock is a **screening tool**, not a substitute for on-site WBGT.

### Rules are data, not if-statements

It'd be shorter to write `if (heatIndex >= 95)` inside the scheduler. It'd also be
unreviewable — a safety officer can't audit an if-statement buried three classes deep, and
adding a second state would mean editing scheduling logic instead of adding a row.

Each `HeatRule` carries its trigger, its requirement in plain language, its rest cadence, and
**its citation**. Adding a state is one new class; the scheduler doesn't change.

### Uncertainty is a first-class field

Every ruleset declares a `VerificationStatus`. ShadeClock encodes legal thresholds a supervisor
may act on, and the honest response to *"I believe this is 95°F but I haven't read the
regulation"* is not a code comment nobody reads — it's a field that **travels through to the API
response and the UI**.

All v0.1 rulesets are `UNVERIFIED_SECONDARY_SOURCE` or `GUIDANCE_NOT_LAW`, and there are tests
asserting they say so.

### Other design decisions

**The strictest triggered rule governs**, not the last one listed. At 106°F both the 95°F and
105°F rules fire; taking whichever came last would be a subtle, dangerous bug if the list were
ever reordered.

**An unknown state falls back to guidance, and says so.** A crew in an unmodelled state still
deserves water-and-shade advice — but the plan states plainly that it is *"NOT that state's
law"*.

**An empty forecast is never an all-clear.** Same principle as RefillRadar's `503`: the plan
says *"Do NOT read that as 'no heat risk'"* rather than rendering an empty, reassuring timetable.

**Time is injected.** Nothing calls `LocalDate.now()`; a `Clock` bean is injected so
acclimatisation assertions stay true instead of drifting with the calendar.

---

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/crews` | Create a crew with its workers |
| `GET` | `/api/crews` | List crews |
| `GET` | `/api/crews/{id}/schedule?date=` | **The main endpoint** — the day's plan |
| `GET` | `/api/heat-index?temperatureF=&humidity=` | Heat index with its stated error |
| `GET` | `/api/jurisdictions` | Which states have modelled rules |

---

## Deployment

### Local

```bash
mvn test                  # run the suite
mvn spring-boot:run       # dev server on :8081
mvn package               # build the JAR
java -jar target/shadeclock-0.1.0-SNAPSHOT.jar
```

Point it at the live NWS API:

```bash
java -jar target/shadeclock-0.1.0-SNAPSHOT.jar \
  --shadeclock.forecast-source=nws \
  --shadeclock.nws.user-agent="(yourapp, you@example.com)"
```

NWS asks API callers to identify themselves — requests without a `User-Agent` may be rejected.
The API is free and needs no key.

### Docker

```bash
docker build -t shadeclock .
docker run -p 8081:8081 shadeclock

# with live weather
docker run -p 8081:8081 -e SHADECLOCK_FORECAST_SOURCE=nws shadeclock
```

Multi-stage build: stage 1 has Maven and the JDK, stage 2 copies only the JAR onto a JRE base,
running as a **non-root user**. The build toolchain never reaches production.

### Cloud CI/CD — GitHub Actions

[`.github/workflows/shadeclock-ci.yml`](../../.github/workflows/shadeclock-ci.yml) runs on every
push and PR touching `apps/shadeclock/**`:

1. **Build and test** — JDK 21, `mvn -B verify`, `~/.m2` cached
2. **Upload test reports** — `if: always()`, so they exist when tests *fail*
3. **Build container** — `needs: build`, so no image is built from failing code
4. **Smoke test** — starts the container and asserts `90°F / 70% RH → 106°F`. If the heat index
   ever drifts, every schedule built on it moved, and the build goes red.

The container job is **deliberately not branch-gated** — see the lesson recorded in
[RefillRadar's README](../refillradar/README.md#cloud-cicd--github-actions). Verify everywhere;
only the registry push takes a branch gate.

Two rules worth internalising:
- **Secrets in GitHub Actions Secrets**, never the repo. A secret pushed to git is compromised
  permanently — git keeps history.
- **Tag images with the commit SHA**, never `latest` alone. You cannot roll back to "latest".

---

## Limitations

**Read this before any crew relies on it.**

| # | Limitation | Impact |
|---|---|---|
| 1 | **State thresholds are UNVERIFIED.** Assembled from compliance summaries, not from 8 CCR §3395 — the build environment couldn't reach the regulations site. | The 95°F high-heat treatment is most likely to be wrong: California's provisions differ by industry, and this flattens them into one conservative cadence. **Erring towards more rest is deliberate**, but this is not a compliance determination. |
| 2 | **Heat index ignores direct sun and radiant heat.** NWS: full sun can add **up to 15°F**. | Screening tool only. Real conditions in open sun or near asphalt/machinery are worse than shown. |
| 3 | **NWS client never exercised against the live API** — `api.weather.gov` is blocked in the build environment. | Field names, units and humidity availability need confirming on first live run. |
| 4 | **No authentication.** Crew ids are trusted as supplied. | Rosters name real people and their work patterns. **Must be fixed before exposure.** |
| 5 | **In-memory storage.** | All crews lost on restart. |
| 6 | **Break placement is a convention, not a regulation.** Rest sits at the end of each hour. | Chosen because it's easy to explain and call. No rule mandates that placement. |

### Roadmap

- **v0.2** — verify California against §3395 and flip its status (kills limitation 1); add the
  other six state rulesets; PostgreSQL (5)
- **v0.3** — offline-capable PWA for remote sites; push notifications at break times;
  authentication (4); optional WBGT sensor input (2)
- **Explicit non-goals** — claiming compliance certification; replacing on-site WBGT
  measurement; medical triage of heat illness

---

## Project layout

```
apps/shadeclock/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/java/com/shadeclock/
    │   ├── heat/      HeatIndexCalculator  <- the NWS maths, heavily tested
    │   │              HourlyConditions, HeatRiskBand
    │   ├── rules/     HeatRuleset + California and federal-baseline implementations
    │   │              VerificationStatus  <- uncertainty as a first-class field
    │   ├── crew/      Worker, Crew, AcclimatizationStatus
    │   ├── schedule/  ScheduleBuilder  <- the engine
    │   ├── forecast/  ForecastSource + fixture and NWS implementations
    │   ├── store/     CrewRepository (in-memory for now)
    │   └── api/       REST controller and DTOs
    └── test/java/     80 tests, no network, no database
```

Every public class and method carries Javadoc explaining **why it exists**, including
trade-offs considered and rejected.

## Sources

- [GW Milken — Nearly 28,000 Work Injuries Every Year are Linked to Hot Weather](https://publichealth.gwu.edu/nearly-28000-work-injuries-every-year-are-linked-hot-weather)
- [CDC/NIOSH — Heat Safety Tool App](https://www.cdc.gov/niosh/heat-stress/communication-resources/app.html)
- [OSHA — Heat Standards](https://www.osha.gov/heat-exposure/standards)
- [NWS WPC — Calculating the Heat Index](https://www.wpc.ncep.noaa.gov/heat_index/details_hi.html)
- [NWS Technical Attachment SR 90-23 (Rothfusz, 1990)](https://www.weather.gov/media/ffc/ta_htindx.PDF)
- [api.weather.gov documentation](https://weather-gov.github.io/api/)
- [8 CCR §3395 — California Heat Illness Prevention](https://www.dir.ca.gov/title8/3395.html)
- [Public Citizen — Scorched States](https://www.citizen.org/article/scorched-states/)
