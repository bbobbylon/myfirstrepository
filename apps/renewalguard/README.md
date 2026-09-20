# RenewalGuard

**Stop people losing benefits they still qualify for.** A deadline-defence app for Medicaid
renewals — because most people who lose coverage never became ineligible, they just missed a
letter.

> **Status: v0.1 — working core engine.** 77 tests passing. Runs offline, no database.
> Not production ready: no auth, in-memory storage, no document storage. See [Limitations](#limitations).
>
> **Not an eligibility decision.** RenewalGuard tracks deadlines and paperwork. Only your
> state agency decides whether you qualify. Your own renewal notice always outranks this app.

Problem research: [`../../proposals/03-renewalguard.md`](../../proposals/03-renewalguard.md)

---

## The problem

- **Up to 70% of Medicaid terminations during redetermination happen for _procedural_
  reasons** — a missed deadline, an undelivered notice, paperwork never completed — **not
  because someone stopped qualifying.**
- During the pandemic unwinding, **~69% of people who lost Medicaid were dropped for
  procedural reasons.** Two figures, different periods, different sources, same magnitude.
- **14 states give enrollees only 10 days to respond** when documents are required.

**Read that again.** If ~70% of terminations are procedural, then the dominant failure mode of
the US safety net is not fraud and not overspending. **It is stationery.** A letter goes to an
old address. A form asks for a pay stub from a job someone left. A 10-day window opens while
someone is in hospital. Coverage ends for someone who was, throughout, fully eligible — then
they re-apply, get re-approved, and the state pays to process them twice.

Nobody wants this outcome, which is a far better position to build in than a problem with a
well-funded defender.

### It's about to get harder

**Public Law 119-21 moves Medicaid expansion adults from annual to six-month renewals**, for
renewals scheduled **on or after 1 January 2027**. CMS issued implementation guidance to state
Medicaid directors on **6 March 2026**. Children, pregnant women, seniors and people with
disabilities stay annual. Reporting puts the affected group at **a little over a quarter of all
enrollees**.

So from 2027 a household can hold two people, same programme, same state, same agency — with
deadlines arriving at different rates. Twice the renewals means twice the chances to miss one,
against a baseline where missing one is already the leading cause of losing coverage.

*⚠️ Sources cite this as both "Section 44108" and "Section 71107" of P.L. 119-21 (both
amending SSA §1902(e)(14)). The substance is consistently reported; the section number isn't.*

---

## Quick start

**Prerequisites:** JDK 21+, Maven 3.9+. No database. No network.

```bash
cd myfirstrepository/apps/renewalguard
mvn test              # 77 tests, fully offline
mvn spring-boot:run   # starts on :8082
```

Port **8082** (RefillRadar has 8080, ShadeClock 8081), so all three run side by side.

### Try it

```bash
ID=$(curl -sS -X POST localhost:8082/api/cases -H 'Content-Type: application/json' -d '{
  "userId":"robert","stateCode":"CA","category":"EXPANSION_ADULT",
  "renewalDueOn":"2026-10-05"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -s "localhost:8082/api/cases/$ID/status" | python3 -m json.tool
curl -s "localhost:8082/api/cases/$ID/reminder" | python3 -m json.tool
```

Real output:

```
Medicaid (ACA expansion adult) in CA
due 2026-10-05  ->  15 days  [HIGH] Act this week
documents ready by : 2026-09-25        <- works BACKWARDS from the deadline
cadence            : every 12 months -> next 2027-10-05
reminding today    : False   ladder [60, 45, 30, 21, 14, 10]
address check      : True

CAVEATS:
  * Your own renewal notice is the authority on your deadline...
  * RenewalGuard tracks deadlines and paperwork. It does NOT decide whether you qualify...
  * From 2027, adults covered through Medicaid expansion renew every SIX months...
```

Note the nuance: *this* renewal is annual because it falls in 2026 — but the caveat still
fires, because the **next** one crosses into the new rule. Warning in advance is the point.

---

## How it works

```
BenefitCase (category + renewal date + address-confirmed date)
      |
RenewalCadence      12 months, or 6 from 2027 for expansion adults
      |
RenewalProjector    days remaining, urgency, documents-ready-by (injected Clock)
      |
ReminderLadder      60 / 45 / 30 / 21 / 14 days, then DAILY inside 10
      |
ReminderComposer    the actual words, with three hard copy rules
```

### Four decisions worth understanding

**1. The deadline is not the deadline.** `documentsReadyBy` works *backwards* by the response
window. If an agency asks for a pay stub with 10 days to reply, you need it **before** the
request lands, not after.

**2. We assume the shortest response window, and say so.** Reporting says 14 states give only
10 days — but **I don't know which 14**, and inventing a plausible state list would be
fabrication. So RenewalGuard assumes 10 days unless you tell it otherwise from your notice.
The asymmetry is the argument: *assuming 30 days when you have 10 loses your coverage;
assuming 10 when you have 30 just means you finish early.*

**3. A passed deadline is CRITICAL, not dismissed.** Unlike RefillRadar — where a passed date
usually meant stale data — here it's the actual emergency, and reinstatement is often still
possible. The message says so outright:

> *"This does NOT automatically mean you have lost coverage… Many people are dropped for
> paperwork reasons while still qualifying. Call your state agency today."*

Going quiet at the moment of failure would be the worst possible behaviour.

**4. Escalation, not repetition.** Reminders every day for two months train people to ignore
them — and an ignored reminder is worse than none, because it carries false reassurance
("the app would have told me"). Sparse far out, daily inside the final 10 days.

### Three copy rules, enforced by tests

1. **Never say whether someone qualifies.** A message implying "you probably won't qualify
   anyway" could talk someone out of a renewal they'd have won — the exact harm this app
   exists to prevent, delivered by the app itself.
2. **Never ask for sensitive data,** and say so explicitly. This audience is heavily targeted
   by benefits scams; a legitimate service that behaves like one teaches the wrong reflex.
3. **Always defer to the notice.** *"If our date and your notice disagree, believe the notice."*

### Why there's no document locker in v0.1

The proposal called for an encrypted locker holding photos of pay stubs, leases and IDs.
**Deliberately not built**, and worth saying why rather than quietly dropping it.

Storing identity documents for low-income households concentrates exactly what an identity
thief wants, belonging to people least able to absorb the consequences. Doing it responsibly
needs encryption at rest and in transit, key management, retention limits, access logging and a
real security review — none of which is a v0.1 afternoon, and all of which is worse than
useless done badly.

So v0.1 ships the ~80% of the value carrying none of that risk: **a checklist that tells you
exactly what to find and where it usually lives.** Holding the files is v0.2, with a security
review attached.

**What it never collects:** no SSN, no income figure, no household composition, no immigration
status. RenewalGuard tracks *when* and *what paperwork* — so it has no business holding the data
an eligibility determination would need. The most secure data is the data you chose not to
collect.

---

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/cases` | Start tracking a case |
| `GET` | `/api/users/{userId}/cases` | List a user's cases |
| `GET` | `/api/cases/{id}/status` | **Main endpoint** — deadline, urgency, checklist, caveats |
| `GET` | `/api/cases/{id}/reminder` | The exact message that would be sent |

---

## Deployment

### Local

```bash
mvn test
mvn spring-boot:run                   # :8082
mvn package && java -jar target/renewalguard-0.1.0-SNAPSHOT.jar
```

### Docker

```bash
docker build -t renewalguard .
docker run -p 8082:8082 renewalguard
```

Multi-stage build, JRE-only runtime stage, runs as a **non-root user**.

### Cloud CI/CD — GitHub Actions

[`.github/workflows/renewalguard-ci.yml`](../../.github/workflows/renewalguard-ci.yml), on every
push and PR touching `apps/renewalguard/**`:

1. **Build and test** — JDK 21, `mvn -B verify`, `~/.m2` cached
2. **Upload test reports** — `if: always()`
3. **Build container** — `needs: build`, **not branch-gated** (see
   [why](../refillradar/README.md#cloud-cicd--github-actions))
4. **Smoke test** — starts the container, POSTs a real case, asserts the response

The `--retry-all-errors` curl fix is in from the start; this app never had to learn that one
the hard way.

---

## Limitations

| # | Limitation | Impact |
|---|---|---|
| 1 | **Response-window figures are single-source.** "14 states / 10 days" and the 10-day notice rule are unverified against regulation text. | They shape the reminder cadence only. Never quoted to a user as legal fact. |
| 2 | **No document storage.** | Deliberate — see above. Checklist only in v0.1. |
| 3 | **No authentication.** `userId` trusted as supplied. | A case reveals someone is on Medicaid in a given state. **Must be fixed before exposure.** |
| 4 | **In-memory storage.** | All cases lost on restart. |
| 5 | **No actual sending.** Reminders are composed and previewable, not delivered. | The scheduled sweep + SMS/email is v0.2. **SMS is mandatory, not optional** — this audience is least reliably reachable by email. |
| 6 | **SNAP not modelled.** | Accepted and flagged, never silently given Medicaid's cadence. |
| 7 | **Renewal date is user-entered.** No agency API exists to read it from. | If their notice disagrees, the notice wins — and the app says so. |

### Roadmap

- **v0.2** — PostgreSQL (4); scheduled daily sweep + Twilio SMS and email with delivery receipts (5);
  encrypted document locker behind a security review (2)
- **v0.3** — authentication (3); SNAP cadence rules (6); per-state response windows once verified (1)
- **Explicit non-goals** — determining eligibility; auto-filing renewals; collecting SSN or income

---

## A note on stack choice

The proposal recommended **Python/Django** for this app, and that recommendation was sound —
Django's admin, auth and forms handling would give you a big head start on something that is
fundamentally *forms, users and deadlines*.

It's built in **Java/Spring Boot** anyway, and that's a defensible trade rather than a mistake:
one language and one CI pattern across all the apps in this repo, matching the Java already
here. The proposal flagged this as a genuine judgement call, not a correctness issue.

**BirthPath will be Python**, where the geospatial case is decisive rather than marginal —
variety where it's justified, not for its own sake.

## Sources

- [Commonwealth Fund — Reducing Medicaid Churn](https://www.commonwealthfund.org/publications/issue-briefs/2025/jun/reducing-medicaid-churn-policies-promote-stable-health-coverage)
- [CMS — State Medicaid Director letter SMD# 26-001 (6 March 2026)](https://www.medicaid.gov/federal-policy-guidance/downloads/smd26001.pdf)
- [Georgetown CCF — CMS Guidance on 6-Month Medicaid Renewals for Expansion Adults](https://ccf.georgetown.edu/2026/03/06/cms-releases-guidance-on-6-month-medicaid-renewals-for-expansion-adults/)
- [KFF — Medicaid Eligibility, Enrollment and Renewal Policies](https://www.kff.org/medicaid/medicaid-and-chip-eligibility-enrollment-and-renewal-policies-as-states-prepare-for-major-medicaid-policy-changes/)
- [Urban Institute — More-Frequent Medicaid Redeterminations](https://www.urban.org/urban-wire/more-frequent-medicaid-redeterminations-would-reduce-health-insurance-coverage-and)
