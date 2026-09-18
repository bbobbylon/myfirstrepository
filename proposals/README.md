# Five App Proposals — Problems Worth Solving in 2026

**Author:** Robert (with Claude Code)
**Date:** 18 September 2026
**Status:** Proposal / pre-implementation

---

## What this is

Five app concepts, each aimed at a problem that is **documented in public data**, not
guessed at. Every headline number below is sourced, and where sources disagree with each
other I say so instead of picking the most dramatic one.

Read this file first. Each app then has its own detailed page:

| # | App | Problem it attacks | Difficulty | Page |
|---|-----|--------------------|-----------|------|
| 1 | **ShadeClock** | ~28,000 work injuries/year linked to heat; the official government app is unmaintained | Medium | [01-shadeclock.md](01-shadeclock.md) |
| 2 | **RefillRadar** | 227 active US drug shortages and rising; patients find out at the pharmacy counter | **Easiest** | [02-refillradar.md](02-refillradar.md) |
| 3 | **RenewalGuard** | ~70% of Medicaid terminations are paperwork failures, not ineligibility | Medium | [03-renewalguard.md](03-renewalguard.md) |
| 4 | **SafeWord** | Americans 60+ lost $7.7B+ to fraud in 2025; AI voice cloning is the new vector | Medium | [04-safeword.md](04-safeword.md) |
| 5 | **BirthPath** | 1 in 3 US counties are maternity care deserts | **Hardest** | [05-birthpath.md](05-birthpath.md) |

---

## How I chose these five

I started with a much longer list — food waste, e-waste, local news deserts, small-business
ransomware, medication adherence, hospital price transparency, insurance claim appeals — and
cut it down using four filters:

**Filter 1 — Is the problem real and measured?**
If I could not find a credible number attached to the problem, it was cut. "Loneliness is
bad" is true but unmeasurable for our purposes; "227 drugs are in active shortage" is a
number you can build an alert against.

**Filter 2 — Is there free, open, legal data to build on?**
This is the filter that killed the most ideas. An app is only as good as its data supply,
and as a solo developer you cannot buy a commercial data feed. All five survivors run on
**free government APIs** (National Weather Service, openFDA, CMS, HRSA) — no API key
negotiation, no vendor bills.

**Filter 3 — Is the space already saturated?**
I researched competitors honestly, and **this filter eliminated two of my original
favourites**:

- ❌ **AI insurance-denial appeal writer.** I loved this idea — prior-auth denials run
  12–18% depending on market, denials are rarely appealed, and appeals frequently succeed.
  A huge asymmetry. But the space already has [Counterforce Health](https://www.counterforcehealth.org/)
  (free, claims ~70% success), [Claimable](https://www.pymnts.com/news/artificial-intelligence/2026/insurance-denials-meet-their-match-in-ai-powered-appeals/)
  (~$50, claims ~80%), and [Fight Health Insurance](https://fighthealthinsurance.com/).
  When a well-funded competitor is already giving it away free, that is a bad first project.
- ❌ **AI summariser for local government meetings.** News deserts are a genuine crisis
  (212 news-desert counties, ~50M Americans with little or no local news). But
  [Documenters](https://www.documenters.org/), [City Scrapers](https://github.com/City-Bureau/city-scrapers),
  Aware, CivicDigest and CivicSummary are all already here.

I am telling you about the ideas I *rejected* because knowing why something was cut is worth
as much as knowing why something was kept.

**Filter 4 — Can one person actually ship a v1?**
Ambition is free; shipping is not. Each proposal below has a deliberately small v0.1 scope.

---

## Sourcing and confidence — please read this bit

Your standing instruction to me is *never lie, always fact-check, flag uncertainty*. So:

### What I could not do
This session's network policy **blocked direct access** to several primary sources I tried
to open: `fbi.gov`, `ic3.gov`, `aarp.org`, `kff.org`, `caregiving.org`, and
`localnewsinitiative.northwestern.edu`. I could search them but not open the source
documents. **Every number below therefore comes from search-index summaries and secondary
reporting, not from a PDF I read end to end.** Where I could cross-check the same number
across several independent outlets, I mark it Verified ×N. You should re-confirm anything
load-bearing before you build a business on it.

### Confidence legend
- **Verified ×N** — the same figure appeared consistently across N independent searches/outlets.
- **Single source** — found once. Treat as indicative.
- **⚠️ Disputed** — sources actively disagree. I show both.

### Known discrepancies I found
1. **Elder fraud year-over-year increase.** Some outlets report the 2025 IC3 elder-fraud
   figure as "+59% in losses, +37% in victim count"; others report "a 37 percent increase in
   losses." The **$7.7B across 201,266 complaints** base figure is consistent everywhere
   (Verified ×3); the *growth rate* is not. I could not open ic3.gov to settle it.
2. **Prior-auth appeal overturn rate (ACA Marketplace).** One KFF summary gave **32%**
   overturned; another gave **43%**. Possibly average-vs-median, or all-requests vs standard
   requests. The consistent, directionally safe claim is: denials are rarely appealed, and
   a large share of appeals succeed, with **enormous variation between insurers (16%–93%)**.
3. **States with enforceable heat standards.** Counted as **7** (CA, CO, MD, MN, NV, OR, WA)
   by most 2026 sources; one outlet says 6. Minor, but it matters for ShadeClock's rules engine.
4. **Heat-related deaths.** I saw "record 2,415 in 2023", "2,394 in 2024", and "1,837 in 2025
   (provisional)" in the same result set — the 2023/2024 figures are close enough to be
   confusable. I have therefore leaned on the **injury** data (better sourced) rather than
   the mortality data.

### One claim I deliberately excluded
You will often see "**60% of small businesses close within 6 months of a cyberattack**." It
surfaced in my research, it is repeated constantly, and it has been widely criticised as
lacking a traceable primary source. **I have not used it.** Flagging it because you will
certainly meet it in the wild.

---

## Which one should you build first?

**Build RefillRadar (#2) first.** My reasoning:

- The data source is a **single, free, well-documented REST API** — `openFDA` at
  `https://api.fda.gov/drug/shortages.json`. No scraping, no auth negotiation, no legal grey area.
- The core logic is *date arithmetic and set intersection*: "is any drug on this user's list
  also on the shortage list, and is their refill due soon?" That is genuinely achievable.
- It is a **near-perfect fit for Java**, which is what your repo is already in. It is a
  scheduled-job-plus-database app, which is Java's home turf.
- It fails safely. If the app is wrong, a user checks with their pharmacist — which is what
  they would have done anyway. Compare to BirthPath, where being wrong has real stakes.

Then **ShadeClock (#1)**, which adds geospatial and offline concerns. Save **BirthPath (#5)**
for last — it is the most valuable and the most dangerous to get wrong.

---

## A note on language choice, since your repo is Java

Your instinct will be to write everything in Java because that is what you know. Sometimes
that is right, sometimes it is not, and knowing the difference is a real skill.

**The analogy:** think of programming languages like vehicles. Java is a **delivery
truck** — heavy, slow to start, boring to drive, and absolutely the right choice when you
need to haul the same load reliably every day for ten years. Python is a **motorbike** —
you are moving in thirty seconds, it weaves through anything, and you would not use it to
move house. JavaScript/TypeScript is a **car** — the only vehicle that is allowed inside the
building (the browser), so if you need to go indoors, you take the car whether you like it
or not.

Applied here:
- **RefillRadar and ShadeClock backends → Java.** Long-running scheduled jobs, a database,
  strict data types, code that must still work unattended in two years. Truck work.
- **BirthPath → Python.** The geospatial and data-wrangling libraries are simply better and
  the dataset work is exploratory. Motorbike work.
- **Anything with a user interface → TypeScript.** Not a preference, a constraint: browsers
  run JavaScript. You take the car because it is the only thing allowed indoors.

Every app page below explains its stack choice *and why the alternatives are worse for that
specific job*, rather than just asserting one.

---

## Conventions for all five apps

Per your standing preferences, when any of these moves to implementation:

1. **Javadoc on everything.** Every public class and method gets a doc comment explaining
   not just *what* it does but *why it exists*. Non-obvious logic gets inline comments.
2. **README kept live from commit one.** Each app gets its own README containing local setup
   and both local and cloud CI/CD deployment steps, updated as the code changes — never
   written at the end.
3. **No invented facts.** Any statistic in user-facing copy carries a source link.

## Sources

- [GW Milken Institute School of Public Health — Nearly 28,000 Work Injuries Every Year are Linked to Hot Weather](https://publichealth.gwu.edu/nearly-28000-work-injuries-every-year-are-linked-hot-weather)
- [GW Today — Nearly 28,000 Work Injuries Every Year Linked to Hot Weather](https://gwtoday.gwu.edu/nearly-28000-work-injuries-every-year-linked-hot-weather)
- [ASHP — Drug Shortages Statistics](https://www.ashp.org/drug-shortages/shortage-resources/drug-shortages-statistics)
- [Becker's Hospital Review — US drug shortages trending upward in 2026](https://www.beckershospitalreview.com/pharmacy/us-drug-shortages-trending-upward-in-2026/)
- [KFF — Prior Authorization Metrics Provide New Insights into Insurer Practices, but Gaps Remain](https://www.kff.org/patient-consumer-protections/prior-authorization-metrics-provide-new-insights-into-insurer-practices-but-gaps-remain/)
- [MedCity News — KFF: Insurers Denied 12%-18% of Prior Authorization Requests in 2025](https://medcitynews.com/2026/08/kff-insurers-denied-12-18-of-prior-authorization-requests-in-2025/)
- [Medill Local News Initiative — News deserts and social media](https://localnewsinitiative.northwestern.edu/posts/2026/02/10/news-deserts-social-media-local-news-medill-survey/index.html)
- [March of Dimes — Nowhere to Go: Maternity Care Deserts Across the U.S.](https://www.marchofdimes.org/maternity-care-deserts-report)
- [Commonwealth Fund — Reducing Medicaid Churn](https://www.commonwealthfund.org/publications/issue-briefs/2025/jun/reducing-medicaid-churn-policies-promote-stable-health-coverage)
- [Counterforce Health](https://www.counterforcehealth.org/) · [Fight Health Insurance](https://fighthealthinsurance.com/) · [Documenters.org](https://www.documenters.org/) · [City Scrapers](https://github.com/City-Bureau/city-scrapers)
