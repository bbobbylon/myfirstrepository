# SafeWord

**A family shield against AI impersonation scams.** Intervenes at the moment of the call —
before the money moves — with a pre-agreed passphrase, a forced pause, and one-tap
escalation to relatives.

> **v0.2 — circles now belong to somebody.** 71 tests, runs offline. Not production ready:
> no mobile client, and **escalation messages are composed but not sent**.

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
# One-time: create the database the defaults expect.
sudo -u postgres psql -c "CREATE USER safeword WITH PASSWORD 'safeword'"
sudo -u postgres createdb -O safeword safeword

mvn test                # 71 tests, offline (needs the database above)
mvn spring-boot:run
```

**1. Try to give the server the passphrase. It refuses.** (Circle routes need a session from
v0.2, so register and log in first; `-c/-b` keeps the cookies, and `X-XSRF-TOKEN` echoes the
CSRF cookie back.)

```bash
curl -sS -c jar -X POST localhost:8083/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"you","password":"a-long-enough-passphrase"}'
curl -sS -c jar -b jar -X POST localhost:8083/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"you","password":"a-long-enough-passphrase"}'
XSRF=$(grep XSRF-TOKEN jar | awk '{print $7}')

curl -sS -b jar -X POST localhost:8083/api/me/circle \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"name":"The Family","passphrase":"bluebird","members":[]}'
```
```json
{"error": "passphrase_not_accepted",
 "message": "SafeWord does not accept your passphrase, by design. We never store it,
             so we can never leak it. Agree it in person and keep it only in your memories."}
```

**2. Report a cloned-voice grandparent call.** No login, no CSRF token, no account — see
below for why that is deliberate.

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

## v0.2: circles belong to somebody, and survive a restart

v0.1 kept circles in a `HashMap` and took the circle id from the URL. Two consequences, and
the second is the serious one:

1. Every circle vanished on restart.
2. **Anyone holding a circle id could read that family's setup and raise an alarm to them.**

The repair is the one RefillRadar v0.4 used — remove the identifier rather than check it:

```
v0.1   GET  /api/circles/{id}/status      ← the caller picks whose family
v0.2   GET  /api/me/circle/status         ← the session picks
```

Escalation matters most. v0.1 only *composes* messages, so the damage was bounded — but the
moment delivery is wired up, an anonymous `POST /api/circles/{id}/escalate` becomes a way to
cry wolf at someone else's family until they learn to ignore the alert this app exists to
send. There is no `findById` in the repository any more: a method that does not exist cannot
be called by a route somebody adds next year without thinking about ownership.

Persistence and identity shipped together because neither is worth much alone. Accounts that
disappear on restart are not accounts; durable circles nobody owns are still readable by
anyone who guesses an id. And a circle that quietly vanished is worse here than in most
apps — the family still sees the app open and still believes someone will be called.

Authentication, sessions and the login rate limiter are **ported from RefillRadar v0.4–v0.5**
with the same reasoning and the same numbers: sessions rather than JWTs so "my phone was
taken" takes effect now, a delegating password encoder, 5 failures per username and 20 per
address in a 15-minute window that heals by itself.

### What stayed public, on purpose

`/api/pause`, `/api/check-call`, `/api/scam-patterns` and `/api/passphrase/instructions`
need no account. They read and write nobody's data — the same tactics always score the same
— and they are what a person uses **while a suspicious call is happening**. A login prompt
at that moment would protect nothing and cost the only moment this app exists for.

`/api/check-call` is also exempt from the CSRF token for the same reason: it carries no
authority for a cross-site request to borrow, and requiring a token would mean fetching one
before you can ask "is this call a scam?". Over-locking is a quieter failure than
under-locking — nothing looks broken, the app is just useless when it matters — so two tests
and a CI assertion exist specifically to keep these four routes open.

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

| Method | Path | Auth | |
|---|---|---|---|
| `POST` | `/api/auth/register` | — | Create an account |
| `POST` | `/api/auth/login` | — | Start a session |
| `POST` | `/api/auth/logout` | session | End it |
| `POST` | `/api/me/circle` | session | Create your circle (**refuses a passphrase**) |
| `PUT` | `/api/me/circle` | session | Replace it — removing a responder is a save without them |
| `GET` | `/api/me/circle/status` | session | Setup status + what's outstanding |
| `POST` | `/api/me/circle/escalate` | session | Ask your circle for a call |
| `GET` | `/api/pause` | **none** | The pause screen content |
| `POST` | `/api/check-call` | **none** | Score a call from reported tactics |
| `GET` | `/api/scam-patterns` | **none** | The pattern library |
| `GET` | `/api/passphrase/instructions` | **none** | How to agree one safely |

**`/api/circles/{id}/...` is gone.** Not guarded — gone. See the v0.2 section.

Note what has **no endpoint**: any way to send SafeWord a passphrase. There is also no
column for one, which CI checks against the live schema rather than against a response body.

## Deployment

### Local

```bash
sudo -u postgres psql -c "CREATE USER safeword WITH PASSWORD 'safeword'"
sudo -u postgres createdb -O safeword safeword

mvn package && java -jar target/safeword-0.1.0-SNAPSHOT.jar

# Overrides, all optional:
java -jar target/*.jar --spring.profiles.active=memory   # no database, LOSES EVERY CIRCLE
```

Override the database with `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD`. **Passwords
go in a secret manager, never in the image or the repo** — the defaults above exist so a
laptop works out of the box.

**`SESSION_COOKIE_SECURE=true` is required for any deployment served over HTTPS.** It
defaults to `false` so `http://localhost` can log in at all; left false behind TLS, the
session cookie — which *is* the credential — can travel in plaintext. The app logs a warning
naming this setting on every boot where it is false.

**Behind a reverse proxy, set `FORWARD_HEADERS_STRATEGY=framework`** — and only there. The
rate limiter counts the socket's address, which a client cannot forge. Put a proxy in front
without telling the application and every request appears to come from the proxy, so twenty
failures from anyone locks out everyone; set it with a proxy that merely passes
`X-Forwarded-For` through, and an attacker rotates the header for a fresh budget.

### Container

```bash
docker network create sw-net
docker run -d --name sw-db --network sw-net \
  -e POSTGRES_DB=safeword -e POSTGRES_USER=safeword \
  -e POSTGRES_PASSWORD=safeword postgres:16

docker build -t safeword . && docker run -p 8083:8083 --network sw-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://sw-db:5432/safeword safeword
```

### CI

**CI** ([`safeword-ci.yml`](../../.github/workflows/safeword-ci.yml)): build + test against a
`postgres:16` service → container build → smoke test against a real database, asserting that
a stranger gets `401` from both circle routes, that the pause screen and call check still
answer `200` with no credentials, that ten anonymous requests create zero session rows, that
a supplied passphrase is refused (via
[`ci/assert_passphrase_refused.py`](ci/assert_passphrase_refused.py)), and that **no column
in the database can hold a passphrase**.

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
| 3 | ~~No authentication~~ — **resolved in v0.2** | |
| 4 | ~~In-memory storage~~ — **resolved in v0.2** | |
| 5 | **Risk weights are product judgement**, not a validated model | Tuned to fire early: an unnecessary pause costs an awkward call; a missed one averages >$38,000 |
| 6 | **Loss figures from secondary reporting** — IC3 and FBI sites unreachable here | Base figures consistent across sources; the **year-over-year growth rate is disputed (+59% vs +37%)** and is never quoted in-app |
| 7 | **One circle per account** | A person in two families — an adult child of separately-living parents — is not served yet |
| 8 | **Responders are contacts, not accounts** | They cannot log in, see the circle, or confirm they got an alert |
| 9 | **No password reset, no account deletion, no backups** | Same gaps RefillRadar has, and closing an account deletes the circle with it |

### Roadmap

- **v0.3** — Flutter client with push (1); real escalation delivery (2)
- **v0.4** — password reset and account deletion (9); circles a responder can join (8)
- **v0.5** — practice-reminder scheduling; multiple circles per person (7)

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
