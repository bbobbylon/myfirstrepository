# 1. ShadeClock — heat safety for work crews

> **One line:** A supervisor's app that turns the weather forecast into a legally-aware
> work/rest schedule for an outdoor crew, and keeps working when the signal drops.

**Difficulty:** Medium · **Primary language:** Java (backend) + TypeScript (PWA)
**Build order recommendation:** Second

---

## The problem

Heat is an occupational hazard that is measured badly and managed informally.

- A **George Washington University / Harvard T.H. Chan** study, published in *Environmental
  Health*, analysed an OSHA dataset covering workplace injuries in 48 states and found that
  **about 28,000 injuries per year can be linked to work on hot days**. *(Verified ×2 — GW's
  own two press pages, plus secondary coverage in Safety+Health and Carrier Management.)*
- Injury risk **starts rising at a heat index around 85°F**, and rises further **at 90°F and
  above**. *(Verified ×2)*
- The injuries appear **across almost all industry sectors, including indoor jobs** — this is
  not only a construction-and-farming problem. *(Single source — GW)*
- Critically: **"Workers in states with OSHA workplace heat exposure standards appear to have
  a lower risk of injury on hot days."** *(Single source — GW)* That one sentence is the
  entire thesis of this app: **codified rules measurably help.**

### Why now — the gap is structural

OSHA published a proposed federal rule, *Heat Injury and Illness Prevention in Outdoor and
Indoor Work Settings*, on 30 August 2024. The comment period closed January 2025 and hearings
ran to July 2025 — but following the January 2025 regulatory freeze the rule **stalled, with
no target finalisation date as of 2026**. *(Verified ×2)*

So in 2026 there is **no finalised federal heat standard**. Instead:
- OSHA runs a revised **Heat National Emphasis Program** (issued 10 April 2026, active five
  years) targeting 55 high-risk industries for inspection **on any day the NWS issues a heat
  advisory**. *(Single source)*
- Roughly **7 states** have their own enforceable standards: California, Colorado, Maryland,
  Minnesota, Nevada, Oregon, Washington. *(Verified ×2, though one outlet counts 6 — ⚠️ see
  discrepancy note in the index.)*

**The result: a patchwork.** A contractor operating in three states faces three different
rulebooks and no federal baseline. That is exactly the kind of messy conditional logic
software is good at and humans are bad at.

### Why the existing app does not close the gap

There *is* an official app — the **OSHA-NIOSH Heat Safety Tool**. It is the obvious
competitor, so I looked at it properly, and it has documented problems **stated on CDC's own
page and in user reviews**:

- **It is unmaintained.** CDC's page states the app **"is not receiving updates at this time,"**
  and that NIOSH "is evaluating options to resolve this issue." *(Single source — but it is
  CDC's own page, which is the strongest possible source for this claim.)*
- **It reports forecast, not actual conditions.** Users report it "doesn't adjust throughout
  the day to actual temperature and humidity, just what the forecast was," and that it reads
  **7–10°F cooler than actual**.
- **It is single-worker, not crew-level.** No supervisor view, no crew roster.
- **No break scheduling.** Users explicitly ask for "break rotation schedules" — the single
  most useful output — and it does not do it.
- **Heat index has a known blind spot:** it is only valid "if no additional radiant heat
  sources are present, such as fires or hot machinery," and it ignores direct sun.

An abandoned incumbent with public, specific complaints is close to an ideal opening.

---

## The wedge

Do not rebuild a thermometer. Build the thing the incumbent refuses to build: **the schedule.**

| | OSHA-NIOSH Tool | **ShadeClock** |
|---|---|---|
| Audience | One worker | **A supervisor + their crew** |
| Output | A risk colour | **A work/rest timetable with alarms** |
| Rules | Generic advice | **The rule for the state you are standing in** |
| Acclimatisation | Mentioned | **Tracked per worker, per day** |
| Connectivity | Needs signal | **Offline-first** |
| Maintenance | Stalled | Actively maintained |

Four specific differentiators:

1. **Work/rest timetables, not risk colours.** "High risk today" is not actionable.
   "10 minutes rest per hour in shade, starting 11:40, crew rotates in two groups" is.
2. **A jurisdiction-aware rules engine.** Encode each state standard as data, then evaluate
   against conditions and location. Oregon requires ~30 minutes cooling per hour at heat index
   100°F; California requires shade at 80°F and cool-down at 95°F; Maryland triggers at heat
   index 80°F with high-heat provisions at 90°F. *(These are indicative and MUST be re-read
   against the actual regulations before shipping — see Risks.)*
3. **Acclimatisation tracking.** New workers are disproportionately at risk; California
   mandates a two-week acclimatisation window. Software remembers who started Tuesday. A
   foreman juggling twelve people does not.
4. **Offline-first.** Directly answers the "remote location" complaint. Cache the forecast at
   depot Wi-Fi in the morning; the schedule survives all day with no bars.

---

## MVP scope (v0.1) — ruthlessly small

**In:**
- Supervisor creates a crew, adds worker names and start dates
- Pull the day's hourly forecast for a pinned site location
- Compute heat index per hour; apply **one** state's ruleset (pick California — best documented)
- Render a timetable; fire local alarms at break times
- Cache the day's plan on the device

**Out (v0.2+):** multi-state rules, WBGT sensors, wearables, compliance exports, i18n.

---

## Data sources — all free, no key

- **`https://api.weather.gov`** — the National Weather Service API. REST, JSON, **free and
  publicly accessible without an API key**. *(Verified ×2)*
- **NWS HeatRisk** — a NWS/CDC collaboration, calculated daily per location for today + 7
  days, factoring temperature anomalies, time of year, duration of heat, and CDC heat-health
  thresholds. Exposed via the NDFD XML service at `https://digital.weather.gov/xml` with
  query parameter `heatrisk`. *(Verified ×2)*

HeatRisk is the smarter input, because it accounts for the fact that 95°F in May is more
dangerous than 95°F in August — bodies acclimatise seasonally. Heat index does not know that.

---

## Stack, and why

**Backend: Java 21 + Spring Boot.**

*Why:* This is a scheduled-job application. Every morning before shift it must fetch forecasts
for every registered site, recompute schedules, and push notifications — unattended, forever.
Spring's `@Scheduled` plus a real type system is exactly this job.

*Analogy:* Static typing is a **recipe that lists ingredients with units**. Python lets you
write "add sugar." Java forces "add 200g sugar." Writing it is more annoying. Reading it back
in eight months, when a heat-index calculation is silently producing Celsius where you expected
Fahrenheit, the units are what save you. In an app where a wrong number means someone
collapses on a roof, **choose the recipe with units.**

*Why not the alternatives:* Python would be faster to start but you would hand-roll job
scheduling and lose compile-time checks on unit conversions. Node/TypeScript is genuinely
viable, but long-lived scheduled workloads are where the JVM is most comfortable, and — the
honest reason — **it is the language you already have in this repo.** Learning geospatial
scheduling *and* a new language simultaneously is two problems, not one.

**Frontend: TypeScript PWA (React or Svelte).**

*Why a PWA and not native:* A Progressive Web App installs to the home screen and works
offline via a service worker, but ships as a website — **no App Store review, no $99/year
Apple developer account, one codebase for iPhone and Android.** For a solo developer that is
decisive. Native would win on background alarm reliability; that is a real cost, and it is the
right trade to revisit if the app gets traction.

**Database: PostgreSQL.**

---

## Deployment

### Local

```bash
# Prerequisites: JDK 21, Docker, Node 20+
git clone <repo> && cd shadeclock

docker compose up -d db          # Postgres on :5432

./mvnw spring-boot:run           # API on :8080

cd web && npm install && npm run dev   # PWA on :5173
```

Add a `.env.example` (committed) and a `.env` (git-ignored, never committed).

### Cloud CI/CD — GitHub Actions

`.github/workflows/ci.yml` runs on every push:

1. **Build & test** — `./mvnw verify` on JDK 21, Maven cache enabled
2. **Frontend** — `npm ci && npm run build && npm test`
3. **Containerise** — multi-stage `Dockerfile`, push to GitHub Container Registry, tagged with
   the commit SHA (never only `latest` — you cannot roll back to `latest`)
4. **Deploy** — only on `main`, only if steps 1–3 pass

Target a container host (Render, Fly.io, Railway, AWS App Runner, Google Cloud Run all work
for this shape of app). **I have deliberately not quoted free-tier limits or pricing — those
change often and I have not verified current figures.** Check before committing.

Two rules worth internalising now:
- **Secrets live in GitHub Actions Secrets**, never in the repo. A secret pushed to git is
  compromised permanently, even if you delete it — git keeps history.
- **Deploy only from `main`, only on green.** Automating a deploy of broken code just gets you
  to the outage faster.

---

## Risks — what could kill this

1. **🔴 Regulatory accuracy is a liability surface.** The state-rule figures above came from
   secondary summaries, not from the regulations themselves. **Before any real crew relies on
   this, every threshold must be read from the actual state regulation and cited in-app.**
   Ship with a visible disclaimer: *decision support, not a compliance guarantee.*
2. **🟠 Heat index is the wrong metric in the hardest cases.** It ignores radiant heat and
   direct sun. Be honest in-product that this is a *screening* tool.
3. **🟠 Distribution.** Small contractors do not shop for safety software. Realistic routes:
   trade associations, unions, insurers (who pay for heat injuries and therefore have a
   financial motive to prevent them).
4. **🟡 Federal rule uncertainty cuts both ways.** If the stalled OSHA rule is finalised,
   demand jumps overnight — but a single national standard also erodes the multi-state
   complexity that is part of your moat.
5. **🟡 The incumbent could wake up.** NIOSH says it is "evaluating options." A revived
   official free app is a real competitive risk.

## Sources
- [GW Milken — Nearly 28,000 Work Injuries Every Year are Linked to Hot Weather](https://publichealth.gwu.edu/nearly-28000-work-injuries-every-year-are-linked-hot-weather)
- [GW Today — study coverage](https://gwtoday.gwu.edu/nearly-28000-work-injuries-every-year-linked-hot-weather)
- [CDC/NIOSH — Heat Safety Tool App](https://www.cdc.gov/niosh/heat-stress/communication-resources/app.html)
- [OSHA — Heat Standards](https://www.osha.gov/heat-exposure/standards)
- [Ogletree — OSHA's Heat Program to Expire While Heat Standard Stalls](https://ogletree.com/insights-resources/blog-posts/oshas-heat-program-to-expire-while-heat-standard-stalls/)
- [Public Citizen — Scorched States: state laws protecting workers from heat](https://www.citizen.org/article/scorched-states/)
- [NWS HeatRisk](https://www.wpc.ncep.noaa.gov/heatrisk/) · [CDC HeatRisk Tracking](https://ephtracking.cdc.gov/Applications/HeatRisk/)
- [api.weather.gov documentation](https://weather-gov.github.io/api/)
