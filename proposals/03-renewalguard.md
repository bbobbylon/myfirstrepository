# 3. RenewalGuard — stop people losing benefits they still qualify for

> **One line:** A deadline-defence app for Medicaid and SNAP renewals — because most people
> who lose coverage never became ineligible, they just missed a letter.

**Difficulty:** Medium · **Primary language:** Python (or Java) + TypeScript
**Build order recommendation:** Third

---

## The problem

This is the proposal with the widest gap between **how much harm it causes** and **how
boring it sounds**, which is precisely why it is under-built.

- **Up to 70% of Medicaid terminations during redetermination cycles happen for _procedural_
  reasons** — a missed deadline, an undelivered renewal notice, or paperwork never completed
  — **not because the person stopped qualifying.** *(Verified ×2)*
- During the pandemic-era "unwinding", **about 69% of people who lost Medicaid were dropped
  for procedural reasons** rather than being found ineligible. *(Verified ×2 — and the
  independent agreement between these two figures, from different periods and sources, is
  what makes me confident in the ~70% magnitude.)*
- **14 states give enrollees only 10 days to respond** when documentation is required to
  avoid termination. *(Single source)*
- States must send a termination notice **at least 10 days** before case closure. A federal
  Eligibility & Enrollment rule requires **at least 30 days** where data indicates
  ineligibility — but that provision is **subject to a 10-year moratorium on implementation**.
  *(Single source — ⚠️ this is a specific and consequential regulatory claim that I could
  not verify against the rule text, since kff.org and medicaid.gov PDFs were blocked. Verify
  before building rules on it.)*

Separately, on the enrolment side: an estimated **$140 billion in federal benefits go
unclaimed annually** — reportedly **$45B+ Medicaid, $15B+ SNAP, $10B+ EITC**, with the IRS
estimating roughly **1 in 5 eligible workers miss the EITC**. *(Single source for the $140B
breakdown — Link Health. Treat the exact split as indicative.)*

### Read that again

If ~70% of terminations are procedural, then **the dominant failure mode of the US safety net
is not fraud, and not overspending. It is stationery.** A letter goes to an old address. A
form asks for a pay stub from a job someone left. A 10-day window opens while someone is in
hospital. Coverage ends for someone who was, throughout, fully eligible.

Then they re-apply, get re-approved, and the state pays the administrative cost twice. This
churn is bad for the enrollee, bad for the state, and bad for providers. **Nobody wants this
outcome** — which is a much better position to build in than a problem with a well-funded
defender.

---

## Competitive landscape

The best-known tool here is **Propel** (formerly Providers / Fresh EBT), which serves **5M+
people** and is the top-rated EBT app. Its documented features are:

- Instant EBT balance and transaction history
- Managing EBT, WIC, Child Tax Credit and TANF in one place
- **EBT theft protection** — blocking out-of-state and online transactions, suspicious
  transaction monitoring, card locking
- Deals, jobs, benefit updates

**mRelief** focuses on *eligibility screening and application* — helping people get on.

**The gap I am claiming:** these tools concentrate on **getting benefits** and **checking
and protecting your balance**. I found **no confirmation that any of them defends the
recertification deadline** — the moment where ~70% of the losses actually happen.

**⚠️ Honesty flag:** my search "did not contain specific information about mRelief SNAP
renewal reminders or recertification features," and Propel's feature list as published makes
no mention of renewal-deadline management. **Absence of evidence is not evidence of absence.**
Before building, install both apps and check directly. If Propel already does this well, this
proposal weakens considerably and you should know that on day one rather than month six.

---

## The wedge

Not "help me apply." Not "what's my balance." **"Do not let this lapse."**

Three components:

1. **A renewal calendar that knows your state.** Renewal cadence and notice periods vary by
   state and program. Encode them; count down loudly. Escalate: 60 days → gentle, 30 → firmer,
   10 → urgent, daily.
2. **A document locker.** The recurring practical blocker is producing *the same documents
   again*: pay stubs, lease, ID, utility bill. Photograph them once, store them encrypted,
   have them ready. **This is the feature people would actually open the app for** — the
   reminders are the medicine, the locker is the spoonful of sugar.
3. **An address-change nudge.** A huge share of procedural terminations are just undelivered
   mail. Prompting "have you moved since last year? here's how to update it with your state
   agency" is nearly trivial to build and plausibly the single highest-leverage feature here.

---

## MVP scope (v0.1)

**In:** one state, one program (Medicaid); manual entry of renewal month; escalating SMS/email
countdown; encrypted document locker; a static state-specific checklist of what renewal
requires and where to submit it.

**Out:** auto-filing, agency API integration (largely does not exist), eligibility
determination, multi-program.

**The hard constraint:** RenewalGuard **never tells anyone whether they are eligible.** It
tracks deadlines and stores documents. Eligibility determination is the state agency's legal
function, and a wrong answer could cause someone to abandon a renewal they would have won.

---

## Stack, and why

**Backend: Python + Django.** This is the one proposal where I would steer you *away* from
Java, and it is worth understanding why.

*Why Django:* Django ships with a built-in admin interface, a mature auth system, and a
migrations framework. For an app that is fundamentally *forms, users, documents and
deadlines*, Django gives you roughly 60% of the app on day one.

*Analogy:* Spring Boot is a **well-stocked hardware store** — everything you need is there,
but you assemble the furniture. Django is **flat-pack furniture with the screws included** —
less freedom, far less assembly. When you are building the thousandth variation on "a form
with a deadline", you want the flat-pack.

*Honest counterpoint:* if you would rather deepen your Java than learn a second language,
**Spring Boot is entirely capable here** and the consistency with your other projects has
real value. This is a genuine judgement call, not a correctness issue. I would rather tell
you that than pretend there is one right answer.

**Frontend: mobile-first web, TypeScript.**

*Why not a native app:* this audience is disproportionately on **older Android devices with
constrained storage and metered data**. Asking someone to spend 80MB of storage and a chunk
of their data plan to install an app is a real barrier. A fast mobile website is not a
compromise here — **it is the accessible choice.**

**Notifications: SMS is mandatory, not optional.** Email is the channel this audience is
least reliably on, and the entire premise is reaching people who miss letters. Budget for
Twilio (or similar) and US A2P 10DLC registration from the start.

**Storage: encrypted object storage** (S3-compatible) with server-side encryption. Documents
here include pay stubs and IDs — treat a breach as the primary risk, not an edge case.

---

## Deployment

### Local

```bash
# Prerequisites: Python 3.12, Docker
git clone <repo> && cd renewalguard

python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

docker compose up -d db minio     # Postgres + S3-compatible local storage

cp .env.example .env
python manage.py migrate
python manage.py createsuperuser
python manage.py runserver        # http://localhost:8000
```

MinIO gives you an S3-compatible bucket locally, so your storage code path is identical in
development and production. **Do not let dev use the local disk and prod use S3** — that
difference always surfaces as a production-only bug.

### Cloud CI/CD — GitHub Actions

```yaml
name: CI
on: [push, pull_request]
jobs:
  test:
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
      - uses: actions/setup-python@v5
        with: { python-version: '3.12', cache: 'pip' }
      - run: pip install -r requirements.txt
      - run: python manage.py migrate --check
      - run: python manage.py test
      - run: pip-audit                # fail the build on known-vulnerable dependencies
```

Deploy on `main` only: build container → push to GHCR with SHA tag → run `migrate` → release.

**Run migrations as an explicit, separate deployment step.** If migrations run implicitly at
app boot and a container restarts mid-migration, you can corrupt the schema. Making it a
discrete step means a failed migration blocks the deploy instead of half-applying it.

---

## Risks

1. **🔴 You are holding poor people's identity documents.** A breach here is catastrophic and
   falls on the population least able to absorb it. Minimum bar: encryption at rest and in
   transit, no third-party analytics anywhere near the locker, strict retention limits, and a
   security review before real users.
2. **🔴 A missed notification is a direct harm.** If your SMS provider fails silently and
   someone loses coverage, your app caused that. Mitigation: multi-channel (SMS **and** email),
   delivery-receipt tracking, and never claim certainty you do not have.
3. **🟠 50 states, 50 rulebooks.** Coverage costs are linear and unglamorous. Mitigation:
   encode rules as reviewable data, start with one state, expand only with verification.
4. **🟠 Trust and stigma.** Stigma is a documented barrier to benefit uptake. An app asking for
   an SSN will be treated with justified suspicion. Mitigation: do not ask for an SSN at all in
   v0.1; partner with trusted community organisations for distribution.
5. **🟡 Monetisation is genuinely hard.** Users have, definitionally, no money. Realistic paths
   are grants, state/county contracts, health-plan funding (plans lose revenue when members
   churn off), or a provider/FQHC-facing model. **This is a strong nonprofit or civic-tech
   project and a weak venture-backed startup.** Better to know now.
6. **🟡 Propel may already do this.** See the honesty flag above. Verify first.

## Sources
- [Commonwealth Fund — Reducing Medicaid Churn](https://www.commonwealthfund.org/publications/issue-briefs/2025/jun/reducing-medicaid-churn-policies-promote-stable-health-coverage)
- [KFF — Medicaid and CHIP Eligibility, Enrollment, and Renewal Policies](https://www.kff.org/medicaid/medicaid-and-chip-eligibility-enrollment-and-renewal-policies-as-states-prepare-for-major-medicaid-policy-changes/)
- [Medicaid.gov — Eligibility Operations and Enrollment Snapshot, April 2026](https://www.medicaid.gov/resources-for-states/downloads/eligib-oper-and-enrol-snap-apr2026.pdf)
- [PointCare — Medicaid Coverage Continuity in 2026](https://www.pointcare.com/blog/medicaid-coverage-continuity-for-chcs-in-2026-a-practical-guide)
- [Link Health — Bridging the $140 Billion Gap](https://link-health.org/2025/04/22/bridging-the-140-billion-gap-how-we-can-close-the-unclaimed-benefits-crisis/)
- [USDA ERS — SNAP Key Statistics and Research](https://www.ers.usda.gov/topics/food-nutrition-assistance/supplemental-nutrition-assistance-program-snap/key-statistics-and-research)
- [Propel](https://www.propel.app/) · [Propel on Google Play](https://play.google.com/store/apps/details?id=com.propel.ebenefits)
