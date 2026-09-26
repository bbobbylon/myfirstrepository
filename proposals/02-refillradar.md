# 2. RefillRadar — personal early warning for drug shortages

> **One line:** Watches the FDA shortage feed against *your* medication list and warns you
> weeks before your refill fails — while there is still time to do something about it.

**Difficulty:** Easiest of the five · **Primary language:** Java
**Build order recommendation: BUILD THIS ONE FIRST.**

---

## The problem

US drug shortages are persistent, structural, and **currently getting worse**.

- **227 active shortages** at the end of Q2 2026 — up from 223 at the end of Q1 2026 and 216
  at year-end 2025. That is **three consecutive quarterly increases**. *(Verified ×2 — ASHP,
  compiled with the University of Utah, via Becker's Hospital Review and Medical Daily.)*
- This is below the all-time record of **323 in Q1 2024**, so the honest framing is *"high and
  climbing again,"* **not** *"worst ever."* I am being deliberate about this because the
  dramatic version is wrong. *(Verified ×2)*
- **~77% of active shortages began in 2022 or later** — these are not legacy problems slowly
  resolving, they are new and accumulating. *(Single source)*
- **Central nervous system medications are the largest category**, then antimicrobials, then
  chemotherapy and hormone agents tied for third. *(Verified ×2)*

That category ranking matters more than it first appears. CNS drugs include ADHD medication,
antidepressants and anti-epileptics — **drugs where an interrupted supply is not an
inconvenience but a medical event.** Abruptly stopping an anti-epileptic can cause seizures.

### The actual failure mode

Here is the gap, and it is a timing gap, not an information gap.

The FDA publishes shortage data. ASHP publishes shortage data. Both are public. But the
patient's experience is:

> Turn up at the pharmacy on the day you run out → "we can't get it" → now you need a
> prescriber appointment, and a therapeutic alternative, and possibly prior authorisation —
> **starting from zero, with zero doses left.**

The information existed for weeks. It just never reached the one person whose refill date
made it urgent. **The data is public; the personalisation is missing.**

---

## Competitive landscape — honest assessment

This space is **not empty**, and you should know that going in:

- **FDA Drug Shortages Database** and **ASHP's list** — authoritative, but they are
  *reference lists*. You must know to go look, and know your drug's generic name.
- **Drugs.com Current Drug Shortages** — a readable list. Still pull, not push.
- **Medfinder** — from Northeastern; notifies when a medication becomes available nearby.
- **FindRx** — checks availability at nearby independent pharmacies, sends fill requests.
- **HermesRX** — real-time recall notices and national shortage updates.

**The pattern:** existing tools are either *general reference lists* or *"find stock near me
right now"* tools. Both are **reactive** — they help once you already have a problem.

**RefillRadar's wedge is lead time.** The unique input nobody else has is **your refill
date**. Knowing a drug is short is background noise. Knowing *your* drug is short and *your*
last pill is in eleven days is an appointment you book today.

I will be straight with you: this is a moderately contested space and the differentiation is
a workflow insight, not a technology moat. But it is small, well-scoped, uses a clean public
API, and is genuinely useful. **That is what a good first project looks like.**

---

## MVP scope (v0.1)

**In:**
- Add medications by name; resolve to a normalised ingredient via RxNorm
- Store refill date + days-of-supply per medication
- Nightly job pulls the openFDA shortage feed and matches against all user medication lists
- Match + refill within the alert window → email/SMS: *what is short, why, your projected
  run-out date, and a prepared question for your prescriber*
- A "no news" weekly all-clear (silence is ambiguous; people assume the app is broken)

**Out (v0.2+):** pharmacy stock levels, therapeutic-alternative suggestions (clinical
judgement — dangerous to automate early), insurance/prior-auth, EHR integration.

**The explicit non-goal:** RefillRadar **never suggests a substitute drug.** It tells you
there is a supply risk and hands you a well-framed question for a pharmacist. Therapeutic
substitution is a clinical decision. Staying on the correct side of that line is what keeps
this an information tool rather than a regulated medical device.

---

## Data sources — all free

- **openFDA Drug Shortages API** — `https://api.fda.gov/drug/shortages.json`
  - Query syntax: `search=field:term`; `limit` max **100 per call**; **default returns only
    one record if `limit` is unset** — a classic beginner trap. *(Verified ×2)*
  - Covers shortages from manufacturing and quality problems, delays and discontinuations.
- **RxNorm** (NIH/NLM) — maps brand names to normalised ingredients. Essential: a user types
  "Adderall", the FDA feed says "amphetamine aspartate; amphetamine sulfate…". Without
  normalisation your matching silently fails — the worst kind of bug, because it looks like
  good news.
- **FDA Drug Shortages** site for cross-checking.

> **⚠️ Practical note for this repo's dev environment.** The Claude Code web session's egress
> policy currently allows **only** `github.com` and package registries (npm, PyPI, crates,
> Go proxy). I probed it directly: `api.fda.gov`, `open.fda.gov`, `example.com` and
> `cdn.jsdelivr.net` all return **403 CONNECT tunnel failed**. So RefillRadar's live API
> calls **cannot be exercised from a default web session** — they will work fine on your own
> machine. Two implications, and they are good engineering practice regardless:
> 1. Put the openFDA client behind an interface with a **recorded-fixture implementation**, so
>    tests and local development run fully offline against saved JSON responses.
> 2. That is also what makes the matching logic testable in CI — GitHub Actions runners should
>    not depend on a third-party API being up. **Never let your test suite fail because
>    someone else's server is having a bad day.**

---

## Stack, and why

**Java 21 + Spring Boot + PostgreSQL.**

This is the strongest Java fit of all five proposals. The app is: *a scheduled job, a
database, some set logic, and a notification sender.* That is precisely the workload the JVM
ecosystem was built for.

*Why Java specifically here:*
- `@Scheduled` gives you the nightly poll without a separate scheduler
- Spring Data JPA handles persistence with very little code
- Static types prevent a whole class of silent matching bugs
- **It builds directly on the language already in your repo**

*Analogy for the matching logic:* think of two guest lists at a party door. One is "drugs
currently in shortage" (from the FDA). One is "drugs this user takes." You are looking for
names on both lists. The subtlety is that the same guest is on one list as "Robert" and the
other as "Bob" — **RxNorm is the ID check that proves they are the same person.** Nearly all
of this app's real difficulty lives in that one step, not in the scheduling or the emails.

*Why not the alternatives:* Python would be quicker to prototype but you would add Celery or
cron for scheduling, and dynamic typing on a matching pipeline means failures show up as
"no alerts sent" rather than a crash — silence that looks exactly like success. Serverless
(Lambda) suits the nightly job but adds cold-start and local-testing friction you do not need
while learning.

**Notifications:** start with plain email via SMTP — zero cost, zero signup. SMS matters for
this audience (older users, lower smartphone use) but carries per-message cost and US A2P
10DLC registration. Add it in v0.2 once the core works.

---

## Deployment

### Local

```bash
# Prerequisites: JDK 21, Docker
git clone <repo> && cd refillradar

docker compose up -d db                       # Postgres on :5432

cp .env.example .env                          # then fill it in; .env stays git-ignored

./mvnw spring-boot:run                        # API on :8080

# Trigger the nightly job by hand instead of waiting until 03:00
curl -X POST localhost:8080/admin/jobs/shortage-sync
```

That last line matters more than it looks. **Always give a scheduled job a manual trigger.**
Otherwise every test cycle costs you a day, and you will be tempted to "just test in prod."

### Cloud CI/CD — GitHub Actions

`.github/workflows/ci.yml`:

```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env: { POSTGRES_PASSWORD: postgres }
        options: >-
          --health-cmd pg_isready --health-interval 10s
          --health-timeout 5s --health-retries 5
        ports: ['5432:5432']
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: 'maven' }
      - run: ./mvnw -B verify
```

Then a `deploy` job gated on `if: github.ref == 'refs/heads/main'` that builds a container,
pushes it to GitHub Container Registry tagged with the commit SHA, and deploys.

Note the `services:` block — GitHub Actions starts a **real throwaway Postgres** for the test
run. Test against the same database engine you deploy on; "it passed against H2" is a lie
you only discover in production.

**Deploy target:** any container host (Render, Fly.io, Railway, Cloud Run). You also need a
managed Postgres. **I have not verified current free-tier terms for any provider — check
before you rely on one.**

---

## Risks

1. **🔴 The medical-advice line.** Cross it and you are a regulated device. Mitigation: never
   recommend substitutes; always route to a pharmacist; explicit in-app disclaimer.
2. **🟠 Data freshness and false negatives.** Being told "no shortage" when there is one is
   worse than not having the app, because it induces false confidence. Mitigation: always show
   "last checked" and "source: FDA", and frame alerts as *supply risk*, not certainty.
3. **🟠 Name matching is the whole ballgame.** Brand/generic/strength/formulation mismatches
   fail silently. Mitigation: build a test fixture of ~50 real drug names, assert matching,
   run in CI. This is the one place to invest heavily in tests.
4. **🟡 Health data = obligations.** A medication list is sensitive. Storing it for others
   invites HIPAA-adjacent scrutiny. Mitigation for v0.1: collect the minimum, encrypt at rest,
   no third-party analytics, and get informed advice before scaling.
5. **🟡 Shortages could ease.** They may decline as they did after Q1 2024. The app stays
   useful — but the urgency, and the fundraising story, softens.

## Sources
- [openFDA — Drug Shortages API overview](https://open.fda.gov/apis/drug/drugshortages)
- [openFDA — How to use the endpoint](https://open.fda.gov/apis/drug/drugshortages/how-to-use-the-endpoint/)
- [FDA — Drug Shortages](https://www.fda.gov/drugs/drug-safety-and-availability/drug-shortages)
- [ASHP — Drug Shortages Statistics](https://www.ashp.org/drug-shortages/shortage-resources/drug-shortages-statistics)
- [Becker's — US drug shortages trending upward in 2026](https://www.beckershospitalreview.com/pharmacy/us-drug-shortages-trending-upward-in-2026/)
- [Medical Daily — US Drug Shortages Climb to 227 Medications](https://www.medicaldaily.com/drug-shortages-227-q2-2026-ct-contrast-ifosfamide-chemotherapy-ashp-476233)
- [Drugs.com — Current Drug Shortages List](https://www.drugs.com/drug-shortages/)
- [Northeastern — Med Finder app](https://news.northeastern.edu/2023/12/22/drug-shortages-med-finder-app)
