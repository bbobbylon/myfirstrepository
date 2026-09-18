# Backlog — researched, deferred, not dead

Ideas that survived the "is the problem real?" test but were **cut on competitive grounds**,
kept here with the research intact so we don't re-do the work.

The important thing about both entries below: **they were not rejected because they are bad
ideas.** They were rejected because someone else got there first. That is a condition that
can change — a competitor pivots, shuts down, moves upmarket, or a new public dataset opens a
door that was previously shut. Each entry therefore records **what would have to change for
this to come off the backlog.**

> **Status key:** 🅰️ Deferred — strong problem, crowded market
> **Review cadence:** re-check competitors every ~6 months

---

## B1. Insurance denial appeals 🅰️

### The problem (evidence holds up well)

- Prior-authorization denial rates in 2025: **18% ACA Marketplace, 14% Medicaid managed care,
  12% Medicare Advantage**. *(Verified ×2 — KFF, via MedCity News.)*
- KFF's analysis covered **14 insurers across ~71 million enrollees**. *(Single source)*
- **Denials are rarely appealed** — but when they are, **a large share get overturned**.
- Reversal rates vary enormously: **16% to 93% depending on the insurer**. *(Single source)*
- ⚠️ **Disputed:** the ACA Marketplace overturn rate is reported as **32%** in one KFF summary
  and **43%** in another. Possibly average-vs-median or all-vs-standard requests. Unresolved.

The asymmetry is genuinely remarkable: **the appeal usually works, and almost nobody files
one.** That is about as clean a "software should fix this" shape as exists.

### Why it was cut

The obvious product — *an AI that writes your appeal letter* — is **already built, three times
over**:

| Competitor | Price | Claimed success rate |
|---|---|---|
| [Counterforce Health](https://www.counterforcehealth.org/) | **Free** | ~70% |
| [Claimable](https://www.pymnts.com/news/artificial-intelligence/2026/insurance-denials-meet-their-match-in-ai-powered-appeals/) | ~$50/appeal | ~80% |
| [Fight Health Insurance](https://fighthealthinsurance.com/) | Free tier | not stated |

Counterforce (founded early 2025, Durham NC) generates customised appeal letters from your
policy plus a record of successful appeals, and reportedly runs a second AI that **phones the
insurer** to pursue the appeal. Claimable has signed drugmaker deals.

**Competing head-on with a free, better-resourced, further-along product is the single most
reliable way to waste a year.**

### What would bring it back — the live opportunity

There is one angle the incumbents are **not** covering, and it is interesting because the
data only just became available:

> **Prior-authorization denial rates became public for the first time in March 2026.** The
> 2024 CMS federal rule requires insurers on CMS-overseen plans to disclose denial-rate data;
> insurers had to post the first year of data (calendar 2025) **by 31 March 2026**.
> *(Verified ×2)*

Everyone is building tools for **after** you are denied. Nobody appears to be building the
tool for **before you choose a plan** — surfacing "this insurer denies 22% and overturns 16%
on appeal; that one denies 9% and overturns 71%" at open-enrolment time.

That reframes the product from *appeals* (crowded) to **plan-selection transparency** (open,
and newly possible). It is also a much better fit for a solo developer: a public dataset, an
annual usage spike, and no clinical or legal writing.

**Revisit triggers:**
- [ ] Verify nobody has built the plan-shopping angle *(re-check before the next open enrolment)*
- [ ] Counterforce or Claimable shuts down, or moves upmarket to providers only
- [ ] Year two of CMS denial data lands, making year-over-year comparison possible
- [ ] Confirm the CMS data is actually usable — granularity, format, per-plan vs per-insurer

**Complication to resolve first:** annual-use products are hard. People think about health
insurance for about two weeks a year. Distribution would have to be a partnership (employer
benefits teams, brokers, navigator organisations), not direct-to-consumer.

---

## B2. Local government meeting monitor 🅰️

### The problem (evidence holds up well)

- **212 news desert counties** in the US, plus **1,525 counties with just one remaining news
  source**. **~50 million Americans** have limited to no access to local news.
  *(Verified ×2 — Medill Local News Initiative / Northwestern, State of Local News.)*
- **127 local newspapers closed** in a single year; the count has fallen from ~7,500 in 2005
  to about **4,500 today** — a decline of over 3,000. *(Single source)*
- News deserts skew **rural, older, less-educated**, with median household income **$57,456
  vs the $74,580 national average**. *(Single source)*
- Residents substitute social media: **42% use Facebook groups / Nextdoor daily, 41% local TV,
  35% search, 33% friends and family, 30% influencers.** *(Single source)*

That last statistic is the whole story. When a paper closes, **demand for local information
does not disappear — it gets served by whoever shows up.** Currently that is Facebook groups
and influencers. Local government carries on meeting either way; the difference is whether
anyone is in the room.

### Why it was cut

The space is genuinely busy, across both nonprofit and commercial:

- **[Documenters.org](https://www.documenters.org/)** (City Bureau) — trains and **pays**
  people to attend under-reported public meetings and publish results.
- **[City Scrapers](https://github.com/City-Bureau/city-scrapers)** — open source, already
  scrapes and standardises meeting data from city council, school board and agency sites.
- **Aware** — AI summaries of council and school board meetings.
- **CivicDigest** — AI-generated reports on hyperlocal public affairs including zoning, school
  boards and HOAs.
- **CivicSummary** — tracks whether city staff actually **followed through** on decisions.

Five players, one of them open source with the scraping already done.

### What would bring it back

Two angles the incumbents appear to leave open:

1. **Coverage geography.** These tools concentrate where the audience is — metros. The Medill
   data says the need is **rural**, in counties with *one or zero* news sources. Whether the
   small-county long tail is actually served is an open, checkable question.
2. **Personal relevance instead of general summaries.** Everyone builds *"here's what the
   council discussed."* Almost nobody builds *"they discussed **your street**"* — an alert
   when a meeting mentions your address, your child's school, your parcel, or a road you
   drive. A general summary competes with five products; a personal alert competes with none.
   People do not want a newspaper. They want to know when something is about to happen to
   them.

**Revisit triggers:**
- [ ] Audit actual rural coverage of Documenters / Aware / CivicDigest — is the long tail served?
- [ ] Confirm nobody ships address-level or keyword-level personal alerting
- [ ] Any of the commercial players shuts down or goes enterprise-only
- [ ] Cheap, accurate local ASR makes per-county cost negligible

**Complication to resolve first:** small counties are exactly the ones that **don't post video
or audio**. The counties with the greatest need may have the least machine-readable input —
which could flip this from a software project into a hardware-and-volunteers project.

---

## Also researched, not written up

Domains where I gathered evidence before narrowing. Keeping the headline numbers so the
research isn't lost — none of these have a proposal behind them yet.

| Domain | Headline evidence | Why not now |
|---|---|---|
| **Family caregiving** | 59M caregivers, 49.5B hours, **$1.01 trillion/yr** if paid; 88% say they lack support *(AARP, Verified ×2)* | Strong problem; coordination-app graveyard (CareZone shut down). Needs a sharper wedge than "shared care log". |
| **Food waste** | ~133B lbs / ~$161B per year in the US | Very crowded consumer space (Too Good To Go, Olio, Flashfood). |
| **E-waste** | 62Mt in 2022, **+82% since 2010**, heading to 82Mt by 2030; only **22.3%** formally recycled | Real problem, weak app-shaped wedge for a solo dev. |
| **Medication adherence** | Nonadherence linked to ~**$528B/yr** and ~125,000 deaths *(figure is contested — see note)* | Extremely crowded (every pill-reminder app ever). |
| **Small business cyber** | 43% of attacks hit SMBs; 43% of SMBs have no dedicated security staff | Hard to monetise, hard to differentiate, high liability. |
| **Hospital price transparency** | Only **21.1%** of hospitals fully compliant; CMS enforcement of new rules began **1 April 2026** | Interesting newly-open data, same annual-use problem as B1. |

⚠️ **Note on the adherence figure:** the "$300 billion" version of this statistic has been
publicly questioned (Pharmacy Times ran a piece literally titled *"Does Nonadherence Really
Cost the Health Care System $300 Billion Annually?"*). The $528.4B figure traces to a
different analysis. **Do not cite either without checking the primary study.**

## Sources
- [KFF — Prior Authorization Metrics Provide New Insights into Insurer Practices](https://www.kff.org/patient-consumer-protections/prior-authorization-metrics-provide-new-insights-into-insurer-practices-but-gaps-remain/)
- [MedCity News — KFF: Insurers Denied 12%-18% of Prior Authorization Requests in 2025](https://medcitynews.com/2026/08/kff-insurers-denied-12-18-of-prior-authorization-requests-in-2025/)
- [Medical Daily — Reversal rates range from 16% to 93% by insurer](https://www.medicaldaily.com/insurance-denials-reversed-appeal-prior-authorization-data-478653)
- [Counterforce Health](https://www.counterforcehealth.org/) · [Fight Health Insurance](https://fighthealthinsurance.com/) · [PYMNTS — Insurance Denials Meet Their Match in AI-Powered Appeals](https://www.pymnts.com/news/artificial-intelligence/2026/insurance-denials-meet-their-match-in-ai-powered-appeals/)
- [Medill Local News Initiative — news deserts and social media](https://localnewsinitiative.northwestern.edu/posts/2026/02/10/news-deserts-social-media-local-news-medill-survey/index.html)
- [Medill — State of Local News 2025 (PDF)](https://localnewsinitiative.northwestern.edu/assets/slnp/the_state_of_local_news_2025.pdf)
- [Documenters.org](https://www.documenters.org/) · [City Scrapers](https://github.com/City-Bureau/city-scrapers)
- [AARP — Valuing the Invaluable 2026](https://www.aarp.org/pri/topics/ltss/family-caregiving/valuing-the-invaluable-2026-update/)
- [Global E-waste Monitor 2024](https://ewastemonitor.info/the-global-e-waste-monitor-2024/)
- [Pharmacy Times — Does Nonadherence Really Cost $300 Billion Annually?](https://www.pharmacytimes.com/view/does-nonadherence-really-cost-the-health-care-system-300-billion-annually)
- [CMS — Hospital Price Transparency](https://www.cms.gov/priorities/key-initiatives/hospital-price-transparency)
