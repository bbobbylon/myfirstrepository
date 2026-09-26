# ShadeClock

**Heat safety scheduling for outdoor work crews.** Turns a weather forecast into a work/rest
timetable, applies the heat rules of the state you're in, and tracks who isn't yet
heat-adapted.

> **v0.2 — persistence and identity.** 95 tests. Crews live in PostgreSQL, every roster read is
> scoped to the supervisor who owns it, and logins are rate limited. Still not production ready:
> state thresholds are **unverified against regulation text**.
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

JDK 21+, Maven 3.9+, Docker (for PostgreSQL). No network, no API key. Port **8081**.

Since v0.2 this needs a database and a login. Three commands, in order — the shared auth module
has to be installed first, because `com.commonauth:common-auth` is published nowhere and is built
from this repository:

```bash
docker run -d --name shadeclock-db -p 5432:5432 \
  -e POSTGRES_DB=shadeclock -e POSTGRES_USER=shadeclock \
  -e POSTGRES_PASSWORD=shadeclock postgres:16

mvn -f ../../libs/common-auth/pom.xml install -DskipTests   # once, and after any change to it
mvn test                                                    # 95 tests
mvn spring-boot:run
```

No database to hand? `mvn spring-boot:run -Dspring-boot.run.profiles=memory` starts with
in-memory stores. Data is lost on restart, so it is a demo, not a deployment — and
`MemoryProfileTest` runs on every build so this instruction cannot quietly stop working.

**The heat index needs no account**, and that is deliberate — see [API](#api):

```bash
curl -s "localhost:8081/api/heat-index?temperatureF=90&humidity=70" | python3 -m json.tool
curl -s localhost:8081/api/jurisdictions
```

Anything involving a crew needs a session. Register, log in, keep the cookie jar, and echo the
`XSRF-TOKEN` cookie back as a header on every write:

```bash
curl -sS -c jar -X POST localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"foreman","password":"a-long-enough-passphrase"}'

curl -sS -c jar -b jar -X POST localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"foreman","password":"a-long-enough-passphrase"}'

XSRF=$(grep XSRF-TOKEN jar | awk '{print $7}')
```

Then create a crew. The crew belongs to the session, and the schedule is read back under
`/api/me/`:

```bash
CREW=$(curl -sS -b jar -X POST localhost:8081/api/crews \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" -d '{
  "name":"Paving Crew","siteLabel":"Route 12","latitude":34.0522,"longitude":-118.2437,
  "jurisdiction":"CA","workers":[
    {"name":"Maria (10yr veteran)","heatWorkStartedOn":"2016-04-01"},
    {"name":"Dev (started yesterday)","heatWorkStartedOn":"2026-07-14"},
    {"name":"Sam (back from 2wk leave)","heatWorkStartedOn":"2020-01-01",
     "lastAbsenceEndedOn":"2026-07-15"}]}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -s -b jar "localhost:8081/api/me/crews/$CREW/schedule?date=2026-07-15" | python3 -m json.tool
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

| Method | Path | Auth | |
|---|---|---|---|
| `POST` | `/api/auth/register` | — | Create a supervisor account |
| `POST` | `/api/auth/login` | — | Start a session |
| `POST` | `/api/auth/logout` | session | End it, server-side |
| `GET` | `/api/heat-index?temperatureF=&humidity=` | **none** | Heat index with its stated error |
| `GET` | `/api/jurisdictions` | **none** | Which states have modelled rules |
| `POST` | `/api/crews` | session + CSRF | Create a crew — owner comes from the session |
| `GET` | `/api/me/crews` | session | List **your own** crews |
| `GET` | `/api/me/crews/{id}/schedule?date=` | session | **Main endpoint** |
| `DELETE` | `/api/me/crews/{id}` | session + CSRF | Remove a roster when the job ends |

### Two routes are public, and two failure directions are real

The heat index and the jurisdiction list need **no account**, deliberately. They hold nobody's
data — a formula and a list of rule sets, the same answer for every caller. Putting a login in
front of them would mean a worker on a roof at 2pm cannot check whether conditions are dangerous
without an account. That protects nothing and costs exactly the moment the app exists for.

Over-locking is the *quieter* bug: nothing looks broken, the app is merely useless when it
matters. Five tests and a CI assertion keep those two open, and removing the `permitAll` line was
tamper-tested — all five go red.

### What v0.2 closed

v0.1's controller carried its own warning: *"crew ids are trusted as supplied… rosters name real
people and their work patterns."* It understated the problem. The IDOR on the schedule route was
the lesser half — the listing needed no id at all:

```
v0.1   GET    /api/crews                     ← crews.findAll(), open to anyone
v0.2   GET    /api/me/crews                  ← the session decides whose

v0.1   GET    /api/crews/{id}/schedule       ← no check whatsoever
v0.2   GET    /api/me/crews/{id}/schedule    ← owner is in the SQL WHERE clause

v0.1   POST   /api/crews                     ← crew belonged to nobody
v0.2   POST   /api/crews                     ← owner comes from the session
```

**One unauthenticated request used to return every crew in the system**: every worker's name, the
dates from which a week-or-more absence can be inferred, and the GPS coordinates of every work
site. `findAll()` no longer exists on `CrewRepository` — it had no other caller and this app has
no batch job, so it is gone rather than guarded. A method that does not exist cannot be called by
a route somebody adds next year without thinking about who is asking.

**Someone else's crew returns 404, never 403.** A 403 would confirm the id is real, and "real,
just not yours" is what an id enumerator wants to learn. A unit test and a CI assertion both check
that a real-but-not-yours id answers byte-for-byte identically to a fabricated one.

### A bug the tests caught, worth recording

Making the pre-write lookup owner-scoped — the obvious fix — *created* a hole. When a second
supervisor's scoped lookup found nothing, the code treated the id as new and called `save()`; JPA's
`save()` on an existing primary key is a **merge**, so it UPDATEd the first supervisor's row and
reassigned `owner_account_id`. The owner-scoped read made the write unsafe by hiding the row it was
about to clobber. It now refuses outright, loudly, in both the JPA and in-memory implementations —
ids are server-generated UUIDs, so reaching that branch means a bug or an attack, and a silent
no-op would hide both.

Four controls were verified by putting the original bug back and confirming the tests go red: the
owner-blind lookup (2 red, mallory got `200` on another supervisor's crew), the restored open
listing (`200` — the v0.1 vulnerability), the removed hijack guard (the overwrite silently
succeeded), and locking the two public routes (5 red). A test that has never failed is a test
whose value is unmeasured.

## Deployment

### Local

```bash
# 1. The shared auth module, into your local Maven repository. Required first.
mvn -f ../../libs/common-auth/pom.xml install -DskipTests

# 2. A database.
docker run -d --name shadeclock-db -p 5432:5432 \
  -e POSTGRES_DB=shadeclock -e POSTGRES_USER=shadeclock \
  -e POSTGRES_PASSWORD=shadeclock postgres:16

# 3. The app. Flyway migrates on startup; Hibernate then validates against the result and
#    refuses to boot on a mismatch, so schema drift is a failed start, not a silent bug.
mvn package && java -jar target/shadeclock-0.1.0-SNAPSHOT.jar

# Live forecast instead of the fixture. api.weather.gov is free and needs no key, but asks
# callers to identify themselves - and this has never been exercised, see Limitations.
java -jar target/*.jar --shadeclock.forecast-source=nws \
  --shadeclock.nws.user-agent="(yourapp, you@example.com)"
```

### Container

**Build from the repository root, not from this directory:**

```bash
cd ../..                                                 # repo root
docker build -f apps/shadeclock/Dockerfile -t shadeclock .
```

The trailing `.` is load-bearing. The image needs `libs/common-auth`, and a build context rooted
at `apps/shadeclock/` cannot see a sibling directory — Docker would fail resolving
`com.commonauth:common-auth`, which is published nowhere. The Dockerfile installs the module
inside its build stage before packaging the app.

```bash
docker network create shadeclock-net
docker run -d --name shadeclock-db --network shadeclock-net \
  -e POSTGRES_DB=shadeclock -e POSTGRES_USER=shadeclock \
  -e POSTGRES_PASSWORD=shadeclock postgres:16

docker run -p 8081:8081 --network shadeclock-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://shadeclock-db:5432/shadeclock \
  -e SPRING_DATASOURCE_USERNAME=shadeclock \
  -e SPRING_DATASOURCE_PASSWORD=shadeclock \
  -e SESSION_COOKIE_SECURE=true \
  shadeclock
```

### Settings you must get right in production

| Variable | Default | Set it to |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | local Postgres | your database |
| `SESSION_COOKIE_SECURE` | `false` | **`true`** anywhere served over HTTPS — otherwise the session cookie travels in the clear on any accidental `http://` request. The app logs a warning on every boot where it is false, so the insecure setting is visible rather than buried in a file |
| `FORWARD_HEADERS_STRATEGY` | `none` | `framework` **only** behind a proxy you control that overwrites `X-Forwarded-For`. The login throttle counts per source address; with `none` that address is the socket's, which a client cannot forge. Set it without such a proxy and anyone can rotate the header for a fresh budget, or forge someone else's address and spend theirs |

### Cloud CI/CD

[`shadeclock-ci.yml`](../../.github/workflows/shadeclock-ci.yml) runs on every push and PR touching
`apps/shadeclock/**`, `libs/common-auth/**` or the workflow itself. Two jobs:

1. **Build and test** — installs `libs/common-auth`, then `mvn verify` against a real
   `postgres:16` service container. The service is needed because the persistence tests assert
   things a `HashMap` passes trivially and PostgreSQL might not: a nullable `DATE` surviving a
   round trip, `orphanRemoval` actually deleting a departed worker, and the `CHECK` constraints on
   `latitude` and `longitude` firing.
2. **Build container image** (`needs: build`) — builds from the repo root, starts its own
   PostgreSQL on a user-defined network, and runs a twelve-part smoke test against the running
   container. Both failure directions are asserted: the old open listing must answer `401`, and
   the heat index must still answer `200` with `106°F` and its `±1.3°F` error to a caller with no
   account. Plus: 20 anonymous requests (refused *and* public) create **0** session rows, a crew
   round-trips with both worker rows landing, **mallory cannot read, delete or list alice's crew**,
   the refusal is byte-identical to one for a fabricated id, a tokenless write gets `403`, a sixth
   wrong password gets `429` (and so does the *correct* one, which is observable proof the refusal
   happens before the password is checked), and Flyway's tables all exist.

Neither job is branch-gated, deliberately. **Verify everywhere, publish only from `main`** — an
image checked only on `main` is checked after the merge it was meant to protect. The registry push
is what belongs behind a branch gate, and it is not wired up yet; when it is, tag with the commit
SHA, never `latest` alone, because you cannot roll back to `latest`.

The `--retry-all-errors` curl flag in the smoke test is load-bearing: `--retry-connrefused` alone
does not cover curl error 56 (connection reset), which is what Docker's port proxy returns while
the container is up but the app has not yet bound its port. See
[RefillRadar's CI notes](../refillradar/README.md#deployment) for both incidents.

## Limitations

| # | Limitation | Impact |
|---|---|---|
| 1 | **State thresholds UNVERIFIED.** From compliance summaries, not 8 CCR §3395 | The 95°F high-heat handling is most likely wrong: California's provisions differ by industry, flattened here into one conservative cadence. **Erring towards more rest is deliberate**, but this is not a compliance determination |
| 2 | **Heat index ignores direct sun and radiant heat** (NWS: up to +15°F) | Screening only. Real conditions in open sun or near asphalt are worse |
| 3 | **NWS client never run live** — `api.weather.gov` blocked here | Field names and units need confirming on first live run |
| 4 | ~~No authentication~~ | **Fixed in v0.2.** Sessions, BCrypt, a login throttle, and every roster read scoped to the supervisor who owns it. The wide-open `GET /api/crews` is gone |
| 5 | ~~In-memory storage~~ | **Fixed in v0.2.** PostgreSQL with Flyway; an unreachable database stops the app starting rather than serving a schedule built from an empty crew |
| 5a | **Rate limiting does not stop a distributed attack** | A few guesses from each of thousands of addresses stays under both limits. No CAPTCHA, no second factor, and the supervisor is never told someone is trying |
| 5b | **No password reset, email verification or account deletion** | And the first admin has to be promoted with SQL |
| 5c | **No backups, retention policy or encryption at rest** | For data that names workers and marks who has been absent for a week or more. `DELETE /api/me/crews/{id}` exists so a roster *can* be removed, but nothing expires one automatically |
| 5d | **One supervisor per crew, with no sharing** | A real site often has two foremen on alternating shifts. Ownership is currently a single account, so the second one cannot see the roster at all |
| 6 | **Break placement is convention, not regulation** | Rest sits at the end of each hour because it's easy to call. No rule mandates it |

### Roadmap

- ~~**v0.2** — PostgreSQL (5); auth (4)~~ — **done.** Both arrived together, because neither is
  much use alone: accounts that vanish on restart are not accounts, and durable rosters nobody
  owns are still listable by anyone
- **v0.3** — verify California against §3395 and flip its status (1); add the other six states;
  shared crews for multi-foreman sites (5d)
- **v0.4** — offline PWA for remote sites; push notifications at break times; optional WBGT sensor
  input (2); distributed-attack defences (5a); password reset and account deletion (5b)

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
