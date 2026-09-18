# myfirstrepository
My First Repository :)
Hi Humans;
  
      Robert here just trying out Git.

---

## 📋 App Proposals (September 2026)

This repo now also holds a research-backed proposal for **five new apps** that address
documented problems in the world today.

👉 **Start here: [`proposals/README.md`](proposals/README.md)**

| # | App | Problem | Difficulty |
|---|-----|---------|-----------|
| 1 | [ShadeClock](proposals/01-shadeclock.md) | ~28,000 heat-linked work injuries/year; the official government app is unmaintained | Medium |
| 2 | [RefillRadar](proposals/02-refillradar.md) | 227 active US drug shortages and climbing; patients find out at the counter | **Easiest** |
| 3 | [RenewalGuard](proposals/03-renewalguard.md) | ~70% of Medicaid terminations are paperwork failures, not ineligibility | Medium |
| 4 | [SafeWord](proposals/04-safeword.md) | Americans 60+ lost $7.7B+ to fraud in 2025; AI voice cloning is the new vector | Medium |
| 5 | [BirthPath](proposals/05-birthpath.md) | 1 in 3 US counties are maternity care deserts | **Hardest** |

**Recommended first build: [RefillRadar](proposals/02-refillradar.md)** — one free public API,
straightforward logic, and a natural fit for the Java already in this repo.

Each proposal page covers the evidence, the existing competition (honestly — two of the
original candidates were cut because the space was already crowded), an intentionally small
MVP scope, the tech stack *with reasoning about why the alternatives are worse*, local and
cloud CI/CD deployment steps, and the risks that could kill it.

### Sourcing note
Every statistic is linked to a source and tagged with a confidence level. Sources that
disagreed with each other are flagged rather than silently resolved, and several primary
sources could not be opened directly during research — this is documented in the
[sourcing and confidence section](proposals/README.md#sourcing-and-confidence--please-read-this-bit).

### Repository layout

```
.
├── README.md                    ← you are here
├── proposals/                   ← the five app proposals
│   ├── README.md                ← index, methodology, sourcing, recommendation
│   ├── 01-shadeclock.md
│   ├── 02-refillradar.md
│   ├── 03-renewalguard.md
│   ├── 04-safeword.md
│   └── 05-birthpath.md
└── *.java                       ← earlier Java practice files
```

### Conventions for these projects, once building starts
- **Javadoc on every public class and method** — explaining *why* the code exists, not just what it does.
- **READMEs kept current from the first commit**, including local setup and cloud CI/CD deployment steps.
- **No unsourced claims** in user-facing copy.
