# SafeWord

**A family shield against AI impersonation scams.** Intervenes at the moment of the call —
before the money moves — with a pre-agreed passphrase, a forced pause, and one-tap
escalation to relatives.

> **v0.1 — working core engine.** 53 tests, runs offline. Not production ready: no auth, no
> mobile client, and **escalation messages are composed but not sent**.

Research: [`../../proposals/04-safeword.md`](../../proposals/04-safeword.md)

## Why

- In 2025, **over 201,000 victims aged 60+ reported losing more than $7.7 billion** to the
  FBI's IC3. Average loss **over $38,000**; more than **12,400 people lost over $100,000
  each**.
- Older adults filed **20% of complaints but bore 37% of losses**.
- 2025 was the **first year IC3 broke out a dedicated AI section**: 22,364 complaints,
  **~$893M**, of which **~$352M** hit the 60+ group — *and that counts only cases where the
  victim realised AI was involved.* A successful voice clone is one nobody detects.

Voice cloning collapsed the cost of a convincing impersonation. The old grandparent scam
relied on a panicked, muffled voice and the victim's imagination. Now the voice **is** the
grandchild's, rebuilt from seconds of social media audio.

**The security insight this rests on:** we spent two decades teaching people that a familiar
voice proves identity. That heuristic is now *actively dangerous*, and it's wired in deep.
You can't patch human intuition — but you can replace the heuristic.

## Quick start

JDK 21+, Maven 3.9+. Port **8083**.

```bash
mvn test                # 53 tests, offline
mvn spring-boot:run
```

**1. Try to give the server the passphrase. It refuses.**

```bash
curl -sS -X POST localhost:8083/api/circles -H 'Content-Type: application/json' \
  -d '{"name":"The Family","passphrase":"bluebird","members":[]}'
```
```json
{"error": "passphrase_not_accepted",
 "message": "SafeWord does not accept your passphrase, by design. We never store it,
             so we can never leak it. Agree it in person and keep it only in your memories."}
```

**2. Report a cloned-voice grandparent call.**

```bash
curl -sS -X POST localhost:8083/api/check-call -H 'Content-Type: application/json' \
  -d '{"signals":["FAMILY_EMERGENCY","SECRECY","URGENCY","IRREVERSIBLE_PAYMENT"]}'
```
```
score 16  ->  STOP - do not send anything
ACTION : Hang up now. Call back on a number you already have.

LIKELY: Family emergency - now with a cloned voice
   - ASK FOR YOUR FAMILY PASSPHRASE. A cloned voice cannot know it.
   - The secrecy request is the tell.

CAVEAT: This is based only on what you told us. It is not a certainty either way.
```

## Three decisions

### 1. A shared secret, NOT deepfake detection

The tempting feature is *"AI-powered voice deepfake detection"*. **Deliberately not built:**

- Detection is an arms race defenders are losing — every improvement is training data for
  the next generator.
- **A false negative is catastrophic**: the app says "this voice appears genuine", the person
  wires $38,000, and *the app caused it.*
- **A shared secret is information-theoretically stronger than any detector.** A perfect
  clone carries no shared secret, and no better model changes that.

**The framing:** this is **two-factor authentication for human beings.** The voice is the
password — and passwords are now trivially stealable. The passphrase is the second factor:
something *known*, not something *sounded*. We accepted long ago that a password alone can't
log you into email; we just never applied that lesson to the telephone.

### 2. The server can never learn the passphrase

It records only **that** one was agreed and **when**. Enforced, not intended:

- The `passphrase` request field exists **solely to be refused** — silently dropping it
  would be exactly the wrong behaviour around a secret.
- A test asserts `FamilyCircle` has **no field that could hold one**; adding it breaks the
  build.
- **CI's container smoke test fails unless the running server refuses a supplied
  passphrase.** Unusual and deliberate: the most important property of this app is something
  it *refuses to do*, so that's what CI checks.

If it never touches our servers, a breach can't leak it. A *leaked* passphrase would be
worse than none, because the family would still trust it.

### 3. Urgency is the attack; friction is the countermeasure

The time pressure isn't incidental — it **is** the scam, because it prevents the one thing
that defeats it: stopping to check. So the core interaction is deliberately slow: one button,
one unskippable screen, facts true regardless of what the caller says.

> *No real organisation asks for gift cards.*
> *No real emergency gets worse if you take ten minutes.*
> *A familiar voice no longer proves who it is.*
> *"Don't tell anyone" is the biggest warning sign there is.*

**It scores tactics, never voices.** Every signal is something the *caller did*, observable
without technical judgement. Those tactics are stable across scam generations because they
exploit psychology, not technology.

**Escalation costs no dignity.** The message to relatives never says "Mum is being scammed"
— it says she wants a call. If the call turns out genuine, nobody's embarrassed, which keeps
the feature cheap enough to use again. *A tool that costs dignity gets used once.* It also
coaches the responder: *stay calm, don't tell them off.*

## Where SafeWord sits

```
   Scam call  →  Victim believes it  →  Money moves  →  [ Carefull / EverSafe alert ]
       ↑                                                          ↑
   SafeWord acts here                              existing tools act here
```

**Carefull** (~$12.99/mo) and **EverSafe** are good products, but they're *account
monitoring* — they fire **after** a transaction. For a wire or gift cards, instruments these
scams choose precisely because they're irreversible, an alert after the fact tells a family
the money is gone. Different layer; running both is entirely reasonable.

## API

| Method | Path | |
|---|---|---|
| `POST` | `/api/circles` | Create a circle (**refuses a passphrase**) |
| `GET` | `/api/circles/{id}/status` | Setup status + what's outstanding |
| `GET` | `/api/passphrase/instructions` | How to agree one safely |
| `GET` | `/api/pause` | The pause screen content |
| `POST` | `/api/check-call` | Score a call from reported tactics |
| `POST` | `/api/circles/{id}/escalate` | Ask the circle for a call |
| `GET` | `/api/scam-patterns` | The pattern library |

Note what has **no endpoint**: any way to send SafeWord a passphrase.

## Deployment

```bash
mvn package && java -jar target/safeword-0.1.0-SNAPSHOT.jar
docker build -t safeword . && docker run -p 8083:8083 safeword
```

**CI** ([`safeword-ci.yml`](../../.github/workflows/safeword-ci.yml)): build + test →
container build → **smoke test asserting the security invariant** via
[`ci/assert_passphrase_refused.py`](ci/assert_passphrase_refused.py).

> **A CI bug worth recording.** That workflow was first derived from RenewalGuard's by regex
> edit, and the substitution silently didn't match — so it POSTed to `/api/cases`, got a
> Spring 404, and reported a security failure the app hadn't committed. Worse, my review grep
> covered the echo line and the curl flags **but not the URL line**. I checked the parts that
> were easy to grep rather than the one that mattered. Fixed by rewriting the file from
> scratch, moving the assertion into a script that can be run locally, and making it detect a
> 404 body specifically.

## Limitations

| # | Limitation | Impact |
|---|---|---|
| 1 | **No mobile client.** Backend + logic only | The real product needs reliable push and a home-screen presence reachable in a panic — not buildable or verifiable here. **v0.2** |
| 2 | **Escalation composed, not sent** | API says `actuallyDelivered: false` outright. A person who believes help is coming and is wrong is worse off than one who knows it isn't |
| 3 | **No authentication** | A circle names real people and phone numbers. **Must fix before exposure** |
| 4 | **In-memory storage** | Circles lost on restart |
| 5 | **Risk weights are product judgement**, not a validated model | Tuned to fire early: an unnecessary pause costs an awkward call; a missed one averages >$38,000 |
| 6 | **Loss figures from secondary reporting** — IC3 and FBI sites unreachable here | Base figures consistent across sources; the **year-over-year growth rate is disputed (+59% vs +37%)** and is never quoted in-app |

### Roadmap

- **v0.2** — Flutter client with push (1); real escalation delivery (2)
- **v0.3** — auth (3); PostgreSQL (4); practice-reminder scheduling

**Non-goals** — voice deepfake detection (see above); call interception (largely impossible
on iOS); account monitoring (Carefull and EverSafe do it better); fear-based marketing to an
already-targeted audience.

### The hard part isn't the code

Distribution is. **The buyer and the user are different people** — the adult child installs
it, the parent uses it — so setup must be completable *by* the child *for* the parent in one
sitting, possibly remotely. And a passphrase only works if agreed in advance and recalled
under stress, which is behaviour change, not software. **Test with real older adults early;
if it fails, it fails there, not in the code.**

## Sources

[FBI IC3 — 2025 Internet Crime Report](https://www.ic3.gov/AnnualReport/Reports/2025_IC3Report.pdf) ·
[FBI — Crypto and AI Scams](https://www.fbi.gov/news/press-releases/cryptocurrency-and-ai-scams-bilk-americans-of-billions) ·
[AARP — $20.9B in losses](https://www.aarp.org/money/scams-fraud/fbi-ftc-report-2025-losses/) ·
[Malwarebytes — ~$900M to AI scams](https://www.malwarebytes.com/blog/scams/2026/06/americans-lost-nearly-900-million-to-ai-powered-scams-fbi-says) ·
[Carefull](https://getcarefull.com/) · [EverSafe](https://www.eversafe.com/for-families/)
