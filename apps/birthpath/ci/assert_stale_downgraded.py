#!/usr/bin/env python3
"""CI safety check: assert that a stale facility record is downgraded.

This is BirthPath's single most important invariant, so it is verified against a
*running container*, not only in unit tests.

The fixture's "Big Spring Community Hospital" record **says** it delivers babies, but was
last confirmed in January 2025. If that ever surfaces as a confirmed option, BirthPath
becomes a confident directory of hospitals that may no longer deliver - and someone could
be sent to a closed unit in labour.

Kept as a script rather than inline in the workflow so it can be run locally against a
dev server, and so the YAML stays readable.

Usage:
    python3 ci/assert_stale_downgraded.py response.json
"""

from __future__ import annotations

import json
import sys


def main(path: str) -> int:
    """Check the facilities payload for correct downgrading.

    Args:
        path: Path to a JSON file containing an ``/api/facilities`` response.

    Returns:
        Process exit code: 0 if the invariant holds, 1 otherwise.
    """
    with open(path, encoding="utf-8") as handle:
        payload = json.load(handle)

    facilities = payload.get("facilities", [])
    if not facilities:
        print("FAIL: no facilities in response", file=sys.stderr)
        return 1

    failures: list[str] = []

    stale = next((f for f in facilities if "Big Spring" in f["name"]), None)
    if stale is None:
        failures.append("the stale fixture record is missing entirely")
    else:
        if stale["recordedStatus"] != "delivers":
            failures.append(f"expected recordedStatus 'delivers', got {stale}")
        if stale["effectiveStatus"] != "unknown":
            failures.append(
                f"SAFETY: stale record was NOT downgraded - effectiveStatus is "
                f"{stale['effectiveStatus']!r}, expected 'unknown'")
        if not stale["downgraded"]:
            failures.append("SAFETY: stale record is not flagged as downgraded")

    never = next((f for f in facilities if "Pecos" in f["name"]), None)
    if never is not None and never["effectiveStatus"] != "unknown":
        failures.append(
            f"SAFETY: never-verified record was NOT downgraded - got "
            f"{never['effectiveStatus']!r}")

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1

    print(f"OK: stale and never-verified records correctly downgraded "
          f"({len(facilities)} facilities checked)")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
