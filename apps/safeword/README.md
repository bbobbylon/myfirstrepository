# SafeWord

**A family shield against AI impersonation scams.** Intervenes at the moment of the phone
call — before the money moves — with a pre-agreed family passphrase, a forced pause, and
one-tap escalation to relatives.

> **Status: v0.1 — working core engine.** 53 tests passing. Runs offline, no database.
> Not production ready: no auth, no mobile client, and **escalation messages are composed
> but not sent**. See [Limitations](#limitations).

Problem research: [`../../proposals/04-safeword.md`](../../proposals/04-safeword.md)

---

## The problem

- In 2025, **over 201,000 victims aged 60+ reported losing more than $7.7 billion** to the
  FBI's IC3. Average loss **over $38,000**; more than **12,400 people lost over $100,000 each.**
- Older adults filed **20% of complaints but bore 37% of losses.**
- 2025 was the **first year IC3 broke out a dedicated AI section**: 22,364 complaints,
  **~$893M**, of which **~$352M** hit the 60+ group — *and that counts only cases where the
  victim realised AI was involved.* A successful voice clone is one nobody detects.

### What actually changed

Voice cloning collapsed the cost of a convincing impersonation. The old grandparent scam
relied on a panicked, muffled voice and the victim's imagination filling the gap. Now the
voice **is** the grandchild's, rebuilt from a few seconds of social media audio.

**Here's the security insight this whole app rests on:** we spent two decades teaching people
that a familiar voice proves identity. That heuristic is now *actively dangerous*, and it's
wired in deep. You can't patch human intuition — but you can replace the heuristic.

---

## Quick start

```bash
cd myfirstrepository/apps/safeword
mvn test              # 53 tests, fully offline
mvn spring-boot:run   # starts on :8083
```

Port **8083** — all four apps run side by side (8080/8081/8082/8083).

### Try the two things that matter

**1. Try to give the server the passphrase. It refuses.**

```bash
curl -sS -X POST localhost:8083/api/circles -H 'Content-Type: application/json' \
  -d '{"name":"The Family","passphrase":"bluebird","members":[]}'
```
```json
{
  "error": "passphrase_not_accepted",
  "message": "SafeWord does not accept your passphrase, by design. We never store it,
              so we can never leak it. Agree it in person and keep it only in your memories."
}
```

**2. Report a cloned-voice grandparent call.**

```bash
curl -sS -X POST localhost:8083/api/check-call -H 'Content-Type: application/json' \
  -d '{"signals":["FAMILY_EMERGENCY","SECRECY","URGENCY","IRREVERSIBLE_PAYMENT"]}'
```
```
score 16  ->  STOP
HEADLINE : STOP - do not send anything
ACTION   : Hang up now. Call the person or organisation back on a number you already have.

LIKELY: Family emergency - now with a cloned voice
   - ASK FOR YOUR FAMILY PASSPHRASE. A cloned voice cannot know it.
   - Hang up and call that person back on the number you already have.
   - The secrecy request is the tell. Real emergencies do not require secrecy.

CAVEAT: This is based only on what you told us. It is not a certainty either way.
```

---

## Three design decisions

### 1. A shared secret, NOT deepfake detection

The tempting feature is *"AI-powered voice deepfake detection"*. **Deliberately not built**,
and the reasoning is the core of the product:

- Detection is an arms race defenders are losing. Every detector improvement is training data
  for the next generator.
- **A false negative is catastrophic**: the app says "this voice appears genuine", the person
  wires $38,000, and *the app caused it.*
- **A shared secret is information-theoretically stronger than any detector.** A perfect clone
  carries no shared secret whatsoever, and no better model will ever change that.

**The analogy:** this is **two-factor authentication for human beings.** The voice is the
password — and passwords are now trivially stealable. The passphrase is the second factor:
something *known*, not something *sounded*. We accepted long ago that a password alone can't
log you into email. We simply never applied that lesson to the telephone.

### 2. The server can never learn the passphrase

SafeWord records **that** a family agreed a passphrase and **when**. It never receives,
transmits, stores, logs or hashes the phrase.

That's enforced, not intended. The `passphrase` field exists in the request type **only to be
refused** — leaving it out would mean a client that sent one had it silently dropped, and
silent behaviour is exactly what you don't want around a secret. There's a test asserting the
`FamilyCircle` record has no field that could hold one, so adding it breaks the build.

If it never touches our servers, a breach of our servers can't leak it. A *leaked* passphrase
would be worse than none, because the family would still trust it.

### 3. Urgency is the attack; friction is the countermeasure

Every one of these scams runs on manufactured time pressure. The pressure isn't incidental —
it **is** the scam, because it prevents the one thing that reliably defeats it: stopping to
check.

So the core interaction is deliberately slow. One button, one unskippable screen, a handful of
facts true regardless of what the caller is saying:

> *No real organisation asks for gift cards.*
> *No real emergency gets worse if you take ten minutes.*
> *A familiar voice no longer proves who it is.*
> *"Don't tell anyone" is the biggest warning sign there is.*

**It scores tactics, never voices.** Every signal is something the *caller did* — observable
without technical judgement. Those tactics are stable across scam generations because they
exploit psychology, not technology. Cloning changed how convincing the voice is; it didn't
remove the need to manufacture urgency, isolate the target, and demand an irreversible payment.

### Bonus: escalation that costs no dignity

The message to relatives deliberately does **not** say "Mum is being scammed". It says she
wants a call. If the call turns out to be genuine, nobody's been embarrassed — which is what
keeps the feature cheap enough to use again. *A tool that costs dignity gets used once.*

It also coaches the responder: *stay calm, don't tell them off, ask them to hang up and call
back on a number they already have.*

---

## Where SafeWord sits vs. existing products

```
   Scam call  →  Victim believes it  →  Money moves  →  [ Carefull / EverSafe alert ]
       ↑                                                          ↑
   SafeWord acts here                              existing tools act here
```

**Carefull** (~$12.99/mo) and **EverSafe** are good products, but they're *account monitoring*
— they fire **after** a transaction. For a wire or gift cards, instruments these scams choose
precisely because they're irreversible, an alert after the fact tells a family the money is
gone. Different layer, complementary. Running both is entirely reasonable.

---

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/circles` | Create a family circle (**refuses a passphrase**) |
| `GET` | `/api/circles/{id}/status` | Setup status + what's still outstanding |
| `GET` | `/api/passphrase/instructions` | How to agree one safely |
| `GET` | `/api/pause` | The pause screen content |
| `POST` | `/api/check-call` | Score a call from reported tactics |
| `POST` | `/api/circles/{id}/escalate` | Ask the circle for a call |
| `GET` | `/api/scam-patterns` | The pattern library |

Note what has **no endpoint**: any way to send SafeWord a passphrase.

---

## Deployment

### Local

```bash
mvn test
mvn spring-boot:run                 # :8083
mvn package && java -jar target/safeword-0.1.0-SNAPSHOT.jar
```

### Docker

```bash
docker build -t safeword . && docker run -p 8083:8083 safeword
```

Multi-stage build, JRE-only runtime, non-root user.

### Cloud CI/CD — GitHub Actions

[`.github/workflows/safeword-ci.yml`](../../.github/workflows/safeword-ci.yml):

1. **Build and test** — JDK 21, `mvn -B verify`, `~/.m2` cached
2. **Upload test reports** — `if: always()`
3. **Build container** — `needs: build`, not branch-gated
4. **Smoke test — asserts the security invariant.** It POSTs a circle *with a passphrase* and
   fails the build unless the server refuses it. If SafeWord ever starts accepting secrets,
   CI goes red.

That last one is unusual and deliberate: the most important property of this app is a thing it
**refuses to do**, so that's what CI checks.

---

## Limitations

| # | Limitation | Impact |
|---|---|---|
| 1 | **No mobile client.** Backend + logic only. | The proposal called for Flutter/React Native. The real product needs reliable push and a home-screen presence reachable in a panic — not buildable or verifiable in this environment. **v0.2.** |
| 2 | **Escalation is composed, not sent.** | The API says `actuallyDelivered: false` outright. A person who believes help is coming and is wrong is worse off than one who knows it isn't. |
| 3 | **No authentication.** | A circle names real people and phone numbers. **Must be fixed before exposure.** |
| 4 | **In-memory storage.** | Circles lost on restart. |
| 5 | **Risk weights are product judgement**, not a validated model. | Stated in one place so they can be reviewed. Tuned to fire early: an unnecessary pause costs an awkward call; a missed one averages >$38,000 for this group. |
| 6 | **Loss figures are secondary reporting.** IC3 and FBI sites were unreachable from the build environment. | Base figures are consistent across sources; the **year-over-year growth rate is disputed (+59% vs +37%)** and is deliberately never quoted in-app. |

### Roadmap

- **v0.2** — Flutter client with push (1); real escalation delivery via push + SMS (2)
- **v0.3** — authentication (3); PostgreSQL (4); practice-reminder scheduling
- **Explicit non-goals** — voice deepfake detection (see above); call interception/screening
  (deep OS restrictions, largely impossible on iOS); account monitoring (Carefull and EverSafe
  do it better); fear-based marketing to an already-targeted audience

### The hard part isn't the code

Distribution is. **The buyer and the user are different people** — the adult child installs it,
the parent uses it — so setup must be completable *by* the child *for* the parent in one
sitting, possibly remotely. And a passphrase only works if it's agreed in advance and recalled
under stress, which is behaviour change, not software. **Test with real older adults early; if
it fails, it fails there, not in the code.**

## Sources

- [FBI IC3 — 2025 Internet Crime Report](https://www.ic3.gov/AnnualReport/Reports/2025_IC3Report.pdf)
- [FBI — Cryptocurrency and AI Scams Bilk Americans of Billions](https://www.fbi.gov/news/press-releases/cryptocurrency-and-ai-scams-bilk-americans-of-billions)
- [AARP — FBI Report: Internet Crime Losses Hit $20.9 Billion](https://www.aarp.org/money/scams-fraud/fbi-ftc-report-2025-losses/)
- [Malwarebytes — Americans lost nearly $900 million to AI-powered scams](https://www.malwarebytes.com/blog/scams/2026/06/americans-lost-nearly-900-million-to-ai-powered-scams-fbi-says)
- [Carefull](https://getcarefull.com/) · [EverSafe](https://www.eversafe.com/for-families/)
