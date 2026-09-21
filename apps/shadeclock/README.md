# ShadeClock

**Heat safety scheduling for outdoor work crews.** Turns a weather forecast into a work/rest
timetable, applies the heat rules of the state you're in, and tracks who isn't yet
heat-adapted.

> **v0.1 — working core engine.** 80 tests, runs offline. Not production ready: state
> thresholds are **unverified against regulation text**, no auth, no database.
>
> **Decision support, not a compliance guarantee.** Verify every threshold against the
> regulation before any crew relies on it.

Research: [`../../proposals/01-shadeclock.md`](../../proposals/01-shadeclock.md)

## Why

A **George Washington / Harvard** study in *Environmental Health*, analysing OSHA injury data
across 48 states, found **~28,000 injuries a year linked to work on hot days**. Risk starts
climbing at a heat index around **85°F**.

Crucially: *"Workers in states with OSHA workplace heat exposure standards appear to have a
lower risk of injury on hot days."* **Codified rules measurably help** — that's the thesis.

But the rules are a patchwork. As of 2026 there's **no finalised federal heat standard**;
OSHA proposed one in August 2024 and it stalled after the January 2025 regulatory freeze. In
its place: a Heat National Emphasis Program plus roughly **7 states** with their own
enforceable standards (CA, CO, MD, MN, NV, OR, WA). A contractor working three states faces
three rulebooks and no federal floor.

### The official app doesn't close the gap

The **OSHA-NIOSH Heat Safety Tool** exists, but CDC's own page says it **"is not receiving
updates at this time."** Reviews report it reads **7–10°F cooler than actual**, shows
forecast rather than current conditions, and — most tellingly — **has no break scheduling**,
the one output that's actually actionable.

| | OSHA-NIOSH Tool | **ShadeClock** |
|---|---|---|
| Audience | One worker | **A supervisor + crew** |
| Output | A risk colour | **A timetable with break times** |
| Rules | Generic advice | **The rule for your state** |
| Acclimatisation | Mentioned | **Tracked per worker, per day** |

## Quick start

JDK 21+, Maven 3.9+. No database, no network, no API key. Port **8081**.

```bash
mvn test                # 80 tests, offline
mvn spring-boot:run
```

```bash
CREW=$(curl -sS -X POST localhost:8081/api/crews -H 'Content-Type: application/json' -d '{
  "name":"Paving Crew","siteLabel":"Route 12","latitude":34.0522,"longitude":-118.2437,
  "jurisdiction":"CA","workers":[
    {"name":"Maria (10yr veteran)","heatWorkStartedOn":"2016-04-01"},
    {"name":"Dev (started yesterday)","heatWorkStartedOn":"2026-07-14"},
    {"name":"Sam (back from 2wk leave)","heatWorkStartedOn":"2020-01-01",
     "lastAbsenceEndedOn":"2026-07-15"}]}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -s "localhost:8081/api/crews/$CREW/schedule?date=2026-07-15" | python3 -m json.tool
```

```
PEAK: 110F at 14:00

WATCH THESE PEOPLE:
  - Dev (started yesterday)
  - Sam (back from 2wk leave)

7 breaks, 90 min total rest:
  11:50-12:00   98F   10 min rest in shade
  13:45-14:00  107F   15 min rest in shade   <- cadence steps up past 105F
```

**Maria is a ten-year veteran and isn't flagged. Sam is *also* a veteran and *is*** — he's
back from two weeks off and his heat adaptation is gone. That's the case a foreman juggling
twelve people forgets, and why this is software rather than a laminated card.

## Design

```
Forecast → HeatIndexCalculator → HourlyConditions → HeatRuleset → ScheduleBuilder
                (NWS Rothfusz)    (+ NWS risk band)   (rules as     (+ Crew
                                                       DATA)         acclimatisation)
```

**The heat index is the real NWS algorithm** — simple formula below 80°F apparent, Rothfusz
regression above, both correction bands. **Validated against the published NWS chart: 13 of
15 reference points within 0.8°F**, inside the regression's stated ±1.3°F error. (Two
outliers turned out to be *my remembered values* being wrong, not the code — 104°F/35% is
112°F on the chart.)

Two limits are constants, not buried comments:

- **`STATED_ERROR_F = 1.3`** — so nothing renders false precision like `91.4732°F`
- **`FULL_SUN_ADDITION_F = 15.0`** — NWS states full sun can add **up to 15°F**, and the
  chart applies to *shade*. A crew reading a "safe" 88°F may really be at 103°F, the Danger
  band. This is a **screening tool**, not a substitute for on-site WBGT.

**Rules are data, not if-statements.** Each `HeatRule` carries its trigger, requirement,
rest cadence and **citation**. A safety officer can audit a list; nobody can audit an `if`
buried three classes deep. Adding a state is one new class.

**Uncertainty is a first-class field.** Every ruleset declares a `VerificationStatus` that
travels through to the API response. The thresholds came from compliance summaries, not from
8 CCR §3395 — the regulations site is blocked here — so California ships as
`UNVERIFIED_SECONDARY_SOURCE`, with tests asserting it admits that.

**The strictest triggered rule governs**, not the last listed. At 106°F both the 95°F and
105°F rules fire; taking whichever came last would be a subtle bug if the list were
reordered.

**An unknown state falls back to guidance and says so** — plainly *"NOT that state's law"*.
**An empty forecast is never an all-clear.** **Time is injected** via a `Clock` bean.

## API

| Method | Path | |
|---|---|---|
| `POST` | `/api/crews` | Create a crew with workers |
| `GET` | `/api/crews` | List |
| `GET` | `/api/crews/{id}/schedule?date=` | **Main endpoint** |
| `GET` | `/api/heat-index?temperatureF=&humidity=` | Heat index with its stated error |
| `GET` | `/api/jurisdictions` | Which states have modelled rules |

## Deployment

```bash
mvn package && java -jar target/shadeclock-0.1.0-SNAPSHOT.jar
java -jar target/*.jar --shadeclock.forecast-source=nws \
  --shadeclock.nws.user-agent="(yourapp, you@example.com)"

docker build -t shadeclock . && docker run -p 8081:8081 shadeclock
```

`api.weather.gov` is free and needs no key, but asks callers to identify themselves.

**CI** ([`shadeclock-ci.yml`](../../.github/workflows/shadeclock-ci.yml)): build + test →
container build → smoke test asserting `90°F / 70% RH → 106°F`. If the heat index drifts,
every schedule built on it moved, and the build goes red.

The container job is deliberately **not branch-gated**, and the `--retry-all-errors` curl
fix is in from the start — see [RefillRadar's CI notes](../refillradar/README.md#deployment)
for the two incidents that established both.

## Limitations

| # | Limitation | Impact |
|---|---|---|
| 1 | **State thresholds UNVERIFIED.** From compliance summaries, not 8 CCR §3395 | The 95°F high-heat handling is most likely wrong: California's provisions differ by industry, flattened here into one conservative cadence. **Erring towards more rest is deliberate**, but this is not a compliance determination |
| 2 | **Heat index ignores direct sun and radiant heat** (NWS: up to +15°F) | Screening only. Real conditions in open sun or near asphalt are worse |
| 3 | **NWS client never run live** — `api.weather.gov` blocked here | Field names and units need confirming on first live run |
| 4 | **No authentication** | Crew rosters name real people. **Must fix before exposure** |
| 5 | **In-memory storage** | Crews lost on restart |
| 6 | **Break placement is convention, not regulation** | Rest sits at the end of each hour because it's easy to call. No rule mandates it |

### Roadmap

- **v0.2** — verify California against §3395 and flip its status (1); add the other six
  states; PostgreSQL (5)
- **v0.3** — offline PWA for remote sites; push notifications at break times; auth (4);
  optional WBGT sensor input (2)

**Non-goals** — claiming compliance certification; replacing on-site WBGT; medical triage.

## Sources

[GW Milken — 28,000 heat-linked injuries](https://publichealth.gwu.edu/nearly-28000-work-injuries-every-year-are-linked-hot-weather) ·
[CDC/NIOSH Heat Safety Tool](https://www.cdc.gov/niosh/heat-stress/communication-resources/app.html) ·
[OSHA Heat Standards](https://www.osha.gov/heat-exposure/standards) ·
[NWS — Calculating the Heat Index](https://www.wpc.ncep.noaa.gov/heat_index/details_hi.html) ·
[SR 90-23 (Rothfusz, 1990)](https://www.weather.gov/media/ffc/ta_htindx.PDF) ·
[api.weather.gov docs](https://weather-gov.github.io/api/) ·
[8 CCR §3395](https://www.dir.ca.gov/title8/3395.html) ·
[Public Citizen — Scorched States](https://www.citizen.org/article/scorched-states/)
