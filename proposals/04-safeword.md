# 4. SafeWord — a family shield against AI impersonation scams

> **One line:** Intervenes at the moment of the phone call — before the money moves — with a
> pre-agreed family passphrase, a forced pause, and one-tap escalation to a relative.

**Difficulty:** Medium · **Primary language:** TypeScript/Dart (mobile) + Java or Node (backend)
**Build order recommendation:** Fourth

---

## The problem

- In 2025, **over 201,000 victims aged 60+ reported losses of more than $7.7 billion** to the
  FBI's Internet Crime Complaint Center (IC3). The precise figure reported across outlets is
  **201,266 complaints / ~$7.75 billion**. *(Verified ×3)*
- **Average loss for 60+ victims exceeded $38,000**, and **more than 12,400 seniors lost over
  $100,000 each.** *(Verified ×2)*
- Older adults made **20% of complaints but 37% of total reported losses** — the harm is
  sharply disproportionate. *(Single source)*
- 2025 was **the first year IC3's annual report broke out a dedicated AI section**: **22,364
  AI-related complaints, roughly $893 million** in losses. *(Verified ×2)*
- Of that, **~$352 million in AI-related losses hit the 60+ group** — and, importantly, that
  only counts **cases where the victim realised AI was involved.** *(Single source)* The true
  figure is necessarily higher, because a successful voice clone is one the victim never
  detects.

**⚠️ Disputed figure:** sources disagree on the year-over-year growth. Some report
"+59% in losses, +37% in victim count"; others report "a 37 percent increase in losses." The
**$7.7B / 201,266 base is consistent everywhere**; the growth rate is not. I could not open
ic3.gov to resolve it — **do not quote a growth rate without checking the source report.**

### What actually changed

Voice cloning collapsed the cost of a convincing impersonation. The classic "grandparent
scam" used to rely on a panicked, muffled voice and the victim's own imagination filling the
gap. Now the voice **is** the grandchild's, reconstructed from a few seconds of social media
audio. Reporting also describes cloning being **layered into business email compromise** — an
impersonation email, then a follow-up phone call in a cloned CEO or CFO voice.

**Here is the security insight that defines this app:** we have spent two decades teaching
people that a familiar voice proves identity. That heuristic is now **actively dangerous**,
and it is wired in deep. You cannot patch human intuition — but you *can* replace the
heuristic with a better one.

---

## Competitive landscape — and the gap

The established players are **Carefull** (~$12.99/month or $119.99/year, including credit and
identity monitoring and identity theft insurance) and **EverSafe**. Both are serious products:

- **Carefull** monitors bank and credit card accounts for **20+ financial "mistakes"** —
  duplicate transactions, suspect transfers, unusual merchants, missed payments, large
  transactions, credit report changes, new recurring donations — alerting **caregivers** by
  email or text.
- **EverSafe** watches for erratic transactions across savings, checking, investment,
  retirement, credit cards, credit reports, real estate and email. Its **"Trusted Advocate"**
  feature sends suspicious-activity alerts to a designated relative in real time **without
  that person taking over the account.**

These are good products. But look carefully at *when* they act:

```
   Scam call  →  Victim believes it  →  Money moves  →  [ Carefull / EverSafe alert ]
       ↑                                                          ↑
   SafeWord acts here                              existing tools act here
```

**They are detection layers. They fire after the transaction.** For a wire transfer or gift
cards — the instruments these scams deliberately use precisely *because* they are
irreversible — an alert after the fact tells a family the money is gone.

**SafeWord's wedge is the thirty seconds before.** Different layer, complementary rather than
competitive. It is entirely reasonable for a family to run both.

---

## The wedge — three mechanisms

**1. The family passphrase.** A word agreed in advance, in person, never posted online, never
stored in the cloud in plaintext. *"If you're really my grandson, what's our word?"* A cloned
voice reproduces timbre and cadence perfectly and **carries no shared secret whatsoever.**

*Analogy:* this is exactly **two-factor authentication for human beings.** The voice is the
password — and passwords are now trivially stealable. The passphrase is the second factor:
something known, not something sounded. We accepted years ago that a password alone is not
enough to log into email. We have simply never applied that lesson to the telephone.

**2. The enforced pause.** Every one of these scams runs on manufactured urgency — *bail
money now, the IRS is at the door, don't tell anyone.* SafeWord inverts it: press one button
and the app shows a full-screen, unskippable checklist. *No legitimate organisation demands
gift cards. No real emergency gets worse for a ten-minute verification. Hang up and call the
number you already have.* **Urgency is the attack; friction is the countermeasure.**

**3. One-tap escalation.** A single button messages pre-designated family members: *"Mum is on
a call about a money transfer — can someone call her now?"* Many victims are too embarrassed
to ask for help. Make asking cost one tap and carry no explanation.

---

## MVP scope (v0.1)

**In:** family group setup; passphrase agreement flow (generates a prompt to agree it *in
person* — never transmits it); the pause checklist; one-tap escalation via push/SMS; a small
library of current scam patterns.

**Out:** call interception or screening (deep OS restrictions, and on iOS largely
impossible); account monitoring (Carefull and EverSafe do it better — do not compete there);
any AI voice-detection claim.

**A deliberate non-feature — and this is a real decision.** It is tempting to promise
"AI-powered voice deepfake detection." **I recommend against it, firmly.** Detection is an
arms race that defenders are currently losing, and a false negative here is catastrophic:
the app tells someone "this voice appears genuine," they wire $38,000, and the app caused it.
**A shared secret is information-theoretically stronger than any detector** and cannot be
defeated by a better model. Ship the boring thing that actually works.

---

## Stack, and why

**Mobile: Flutter (Dart) or React Native (TypeScript).**

*Why cross-platform:* this app must exist on **both** iOS and Android — the whole design
assumes an older adult and an adult child on different devices. Two native codebases is
roughly double the work for a solo developer.

*Why native is not required here:* unlike ShadeClock, this app needs no offline forecasting or
background scheduling. It needs push notifications, a contact list, and clear screens. Both
frameworks handle that comfortably.

*Why not a PWA here (unlike RenewalGuard):* SafeWord needs **reliable push notifications and
a home-screen presence you can reach in a panic.** Web push on iOS remains more constrained
than native. When the product's core promise is "reachable within seconds during a crisis,"
that constraint is disqualifying.

**Backend: minimal.** Java/Spring Boot or Node — group membership, push tokens, escalation
fan-out. Deliberately thin.

**Design constraint that outranks everything technical:** the primary user may be 78, may have
reduced vision, and will be using this while frightened. **Minimum 18pt type, very high
contrast, one action per screen, no jargon, no dark patterns, no onboarding carousel.** If you
build only one thing well here, build this. An accessible interface is not a nice-to-have on
this project — it *is* the project.

**Security rule, absolute: the passphrase is never transmitted or stored server-side.** The
app prompts families to agree it in person and never handles it. If it never touches your
servers, a breach of your servers cannot leak it. **The most secure data is the data you
chose not to collect.**

---

## Deployment

### Local

```bash
# Prerequisites: Flutter SDK 3.x, JDK 21, Docker
git clone <repo> && cd safeword

docker compose up -d db
./mvnw spring-boot:run              # API on :8080

cd app
flutter pub get
flutter run                         # attached device or simulator
```

### Cloud CI/CD — GitHub Actions

```yaml
name: CI
on: [push, pull_request]
jobs:
  api:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin', cache: 'maven' }
      - run: ./mvnw -B verify

  mobile:
    runs-on: macos-latest        # macOS runner required for any iOS build
    steps:
      - uses: actions/checkout@v4
      - uses: subosito/flutter-action@v2
        with: { channel: stable }
      - run: flutter analyze
      - run: flutter test
      - run: flutter build apk --debug
```

**Mobile CI/CD is meaningfully harder than web, and you should know this before you start:**

- **Code signing.** iOS builds need certificates and provisioning profiles as encrypted CI
  secrets. This is the step that frustrates everyone the first time. Budget a full day.
- **App Store review.** Days of latency per release, and it can reject you. This alone is why
  ShadeClock and RenewalGuard are web-first — **you cannot hotfix through an App Store queue.**
- **Costs:** Apple Developer Program is a paid annual membership; Google Play charges a
  one-off registration fee. **I have not verified current 2026 prices — check directly.**
- **Distribution:** use Firebase App Distribution or TestFlight for testers before store release.

---

## Risks

1. **🔴 Distribution is the real problem, not the code.** Older adults do not browse app
   stores for security software. **The buyer is the adult child; the user is the parent** —
   and the app must be installable and explainable *by* the child *for* the parent, in one
   sitting, possibly remotely. Design the onboarding for that. Realistic channels: senior
   centres, credit unions, AARP-type organisations, libraries, elder law practices.
2. **🔴 Behaviour change is harder than software.** A passphrase only works if agreed in
   advance and remembered under stress. Mitigation: periodic no-pressure practice reminders.
   Test this with real older adults early — **if it fails, it fails here, not in the code.**
3. **🟠 Fear-based marketing is a trap.** Marketing that frightens seniors to sell a
   subscription is itself predatory, and this audience is already a target. Keep the tone calm
   and factual.
4. **🟠 A false sense of security.** Someone who installs it and never agrees a passphrase is
   arguably *worse* off. Mitigation: do not mark setup complete until a passphrase is confirmed
   agreed; nag until it is.
5. **🟡 Scammers adapt.** *"Your grandson told me the word is…"* Mitigation: teach that the
   passphrase is asked **of** the caller, never volunteered, and never repeated to anyone.
6. **🟡 Monetisation.** Carefull sits around $12.99/month for a much broader feature set.
   SafeWord is thinner and cannot command that. Freemium, or family-plan pricing on the adult
   child's account, is more plausible than a senior-paid subscription.

## Sources
- [FBI IC3 — 2025 Internet Crime Report (PDF)](https://www.ic3.gov/AnnualReport/Reports/2025_IC3Report.pdf)
- [FBI — Cryptocurrency and AI Scams Bilk Americans of Billions](https://www.fbi.gov/news/press-releases/cryptocurrency-and-ai-scams-bilk-americans-of-billions)
- [AARP — FBI Report: Internet Crime Losses Hit $20.9 Billion](https://www.aarp.org/money/scams-fraud/fbi-ftc-report-2025-losses/)
- [HousingWire — FBI: Seniors lost $7.75B to cybercrime in 2025](https://www.housingwire.com/articles/fbi-seniors-cybercrime-2025/)
- [Malwarebytes — Americans lost nearly $900 million to AI-powered scams](https://www.malwarebytes.com/blog/scams/2026/06/americans-lost-nearly-900-million-to-ai-powered-scams-fbi-says)
- [AARP — Tech Tools to Help Guard Against Elder Financial Abuse](https://www.aarp.org/personal-technology/tools-to-avoid-elder-financial-abuse/)
- [Carefull](https://getcarefull.com/) · [EverSafe for Families](https://www.eversafe.com/for-families/)
