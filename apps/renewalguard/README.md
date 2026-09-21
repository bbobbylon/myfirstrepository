# RenewalGuard

**Stop people losing benefits they still qualify for.** A deadline-defence app for Medicaid
renewals — because most people who lose coverage never became ineligible, they just missed a
letter.

> **v0.1 — working core engine.** 77 tests, runs offline. Not production ready: no auth, no
> database, no actual sending.
>
> **Not an eligibility decision.** Only your state agency decides whether you qualify. Your
> own renewal notice always outranks this app.

Research: [`../../proposals/03-renewalguard.md`](../../proposals/03-renewalguard.md)

## Why

- **Up to 70% of Medicaid terminations during redetermination are _procedural_** — a missed
  deadline, an undelivered notice, paperwork never returned — **not a finding that someone
  stopped qualifying.**
- During the pandemic unwinding, **~69%** of people who lost Medicaid were dropped for
  procedural reasons. Two figures, different periods and sources, same magnitude.
- **14 states give only 10 days to respond** when documents are required.

**Read that again.** If ~70% of terminations are procedural, the dominant failure mode of
the US safety net is not fraud and not overspending. **It is stationery.** A letter goes to
an old address. A form asks for a pay stub from a job someone left. Coverage ends for
someone who was, throughout, eligible — then they re-apply, get re-approved, and the state
pays to process them twice.

### It's about to get harder

**Public Law 119-21 moves Medicaid expansion adults from annual to six-month renewals**, for
renewals scheduled **on or after 1 January 2027**. CMS issued guidance to state Medicaid
directors on **6 March 2026**. Children, pregnant women, seniors and disabled enrollees stay
annual — reporting puts the affected group at **a little over a quarter of all enrollees**.

So from 2027 a household can hold two people, same programme and same agency, with deadlines
arriving at different rates. Twice the renewals, against a baseline where missing one is
already the leading cause of losing coverage.

*⚠️ Sources cite this as both "Section 44108" and "Section 71107" of P.L. 119-21 (both
amending SSA §1902(e)(14)). The substance is consistent; the section number isn't.*

## Quick start

JDK 21+, Maven 3.9+. No database, no network. Port **8082**.

```bash
mvn test                # 77 tests, offline
mvn spring-boot:run
```

```bash
ID=$(curl -sS -X POST localhost:8082/api/cases -H 'Content-Type: application/json' -d '{
  "userId":"robert","stateCode":"CA","category":"EXPANSION_ADULT",
  "renewalDueOn":"2026-10-05"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -s "localhost:8082/api/cases/$ID/status"   | python3 -m json.tool
curl -s "localhost:8082/api/cases/$ID/reminder" | python3 -m json.tool
```

```
due 2026-10-05  ->  15 days  [HIGH] Act this week
documents ready by : 2026-09-25        <- works BACKWARDS from the deadline
cadence            : every 12 months -> next 2027-10-05
ladder             : [60, 45, 30, 21, 14, 10] then daily
address check      : True

CAVEAT: From 2027, adults covered through Medicaid expansion renew every SIX months...
```

Note the nuance: *this* renewal is annual because it falls in 2026 — but the caveat still
fires, because the **next** one crosses into the new rule. Warning in advance is the point.

## Four decisions

**1. The deadline is not the deadline.** `documentsReadyBy` works *backwards* by the
response window. If an agency asks for a pay stub with 10 days to reply, you need it
**before** the request lands.

**2. Assume the shortest window, and say so.** Reporting says 14 states give only 10 days —
but **I don't know which 14**, and inventing a state list would be fabrication. So it assumes
10 days unless you enter your own from your notice. The asymmetry is the argument: *assuming
30 days when you have 10 loses your coverage; assuming 10 when you have 30 just means you
finish early.*

**3. A passed deadline is CRITICAL, not dismissed.** Unlike RefillRadar — where a passed
date usually meant stale data — here it's the emergency, and reinstatement is often still
possible:

> *"This does NOT automatically mean you have lost coverage… Many people are dropped for
> paperwork reasons while still qualifying. Call your state agency today."*

**4. Escalation, not repetition.** Reminders every day for two months train people to ignore
them — and an ignored reminder carries false reassurance. Sparse far out (60/45/30/21/14),
daily inside the final 10.

### Three copy rules, enforced by tests

1. **Never say whether someone qualifies.** Implying "you probably won't qualify anyway"
   could talk someone out of a renewal they'd have won — the exact harm this app prevents,
   delivered by the app itself.
2. **Never ask for sensitive data,** and say so. This audience is heavily targeted by
   benefits scams; a legitimate service behaving like one teaches the wrong reflex.
3. **Always defer to the notice.** *"If our date and your notice disagree, believe the notice."*

### Why there's no document locker

The proposal called for an encrypted locker holding photos of pay stubs, leases and IDs.
**Deliberately not built.** Storing identity documents for low-income households concentrates
exactly what an identity thief wants, belonging to people least able to absorb it — and doing
it responsibly needs key management, retention limits and a security review.

v0.1 ships the ~80% of value with none of that risk: **a checklist saying what to find and
where it usually lives.** It also collects **no SSN, income, household composition or
immigration status** — a deadline tracker has no business holding eligibility data.

## API

| Method | Path | |
|---|---|---|
| `POST` | `/api/cases` | Start tracking a case |
| `GET` | `/api/users/{id}/cases` | List |
| `GET` | `/api/cases/{id}/status` | **Main endpoint** — deadline, urgency, checklist, caveats |
| `GET` | `/api/cases/{id}/reminder` | The exact message that would be sent |

## Deployment

```bash
mvn package && java -jar target/renewalguard-0.1.0-SNAPSHOT.jar
docker build -t renewalguard . && docker run -p 8082:8082 renewalguard
```

**CI** ([`renewalguard-ci.yml`](../../.github/workflows/renewalguard-ci.yml)): build + test →
container build (`needs: build`, not branch-gated) → smoke test that POSTs a real case. The
`--retry-all-errors` curl fix is in from the start — see
[RefillRadar's CI notes](../refillradar/README.md#deployment).

## Limitations

| # | Limitation | Impact |
|---|---|---|
| 1 | **Response-window figures single-source**, unverified against regulation text | They shape reminder cadence only, never quoted as legal fact |
| 2 | **No document storage** | Deliberate — see above. Checklist only |
| 3 | **No authentication** | A case reveals someone is on Medicaid in a given state. **Must fix before exposure** |
| 4 | **In-memory storage** | Cases lost on restart |
| 5 | **No actual sending.** Reminders are composed and previewable only | The scheduled sweep + SMS/email is v0.2. **SMS is mandatory, not optional** — this audience is least reachable by email |
| 6 | **SNAP not modelled** | Accepted and flagged, never silently given Medicaid's cadence |
| 7 | **Renewal date is user-entered** — no agency API exists | If their notice disagrees, the notice wins, and the app says so |

### Roadmap

- **v0.2** — PostgreSQL (4); daily sweep + Twilio SMS and email with delivery receipts (5);
  encrypted locker behind a security review (2)
- **v0.3** — auth (3); SNAP cadence (6); per-state response windows once verified (1)

**Non-goals** — determining eligibility; auto-filing renewals; collecting SSN or income.

## A note on stack choice

The proposal recommended **Python/Django**, and that was sound — Django's admin, auth and
forms handling suit something that's fundamentally *forms, users and deadlines*.

It's built in **Java/Spring Boot** anyway: one language and one CI pattern across the repo,
matching the Java already here. The proposal flagged this as a judgement call, not a
correctness issue. **BirthPath is Python**, where the geospatial case is decisive rather than
marginal — variety where it's justified, not for its own sake.

## Sources

[Commonwealth Fund — Reducing Medicaid Churn](https://www.commonwealthfund.org/publications/issue-briefs/2025/jun/reducing-medicaid-churn-policies-promote-stable-health-coverage) ·
[CMS SMD# 26-001](https://www.medicaid.gov/federal-policy-guidance/downloads/smd26001.pdf) ·
[Georgetown CCF — 6-month renewals](https://ccf.georgetown.edu/2026/03/06/cms-releases-guidance-on-6-month-medicaid-renewals-for-expansion-adults/) ·
[KFF — eligibility and renewal policies](https://www.kff.org/medicaid/medicaid-and-chip-eligibility-enrollment-and-renewal-policies-as-states-prepare-for-major-medicaid-policy-changes/) ·
[Urban Institute — more-frequent redeterminations](https://www.urban.org/urban-wire/more-frequent-medicaid-redeterminations-would-reduce-health-insurance-coverage-and)
