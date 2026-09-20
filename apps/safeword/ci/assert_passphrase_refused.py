#!/usr/bin/env python3
"""CI security check: assert the server refuses a supplied passphrase.

SafeWord's central promise is that this server never learns a family's passphrase. That
promise is only worth something if it is verified against a *running* server, not only in
unit tests - so this runs against the container in CI.

Kept as a script rather than inline in the workflow for two reasons: it can be run locally
against a dev server, and an earlier inline version was silently broken by a bad regex
edit that left another app's endpoint in place. The check appeared to pass review while
actually POSTing to a URL that did not exist.

Usage:
    python3 ci/assert_passphrase_refused.py response.json
"""

from __future__ import annotations

import json
import sys

EXPECTED_ERROR = "passphrase_not_accepted"


def main(path: str) -> int:
    """Check that the response is a refusal, not a success and not a 404.

    Args:
        path: Path to a JSON file holding the response to ``POST /api/circles``
            with a ``passphrase`` field populated.

    Returns:
        Process exit code: 0 if the server correctly refused, 1 otherwise.
    """
    try:
        with open(path, encoding="utf-8") as handle:
            payload = json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        print(f"FAIL: could not read {path}: {exc}", file=sys.stderr)
        return 1

    # Catch the specific mistake that got shipped once: posting to a path that does not
    # exist returns a Spring 404 body, whose "error" key is "Not Found". Without this the
    # check would report a clean failure rather than the real cause.
    if payload.get("status") == 404:
        print(
            f"FAIL: the smoke test hit a path that does not exist "
            f"({payload.get('path')!r}). The check never reached the security invariant.",
            file=sys.stderr,
        )
        return 1

    if payload.get("error") != EXPECTED_ERROR:
        print(
            f"FAIL: SECURITY - the API did not refuse a supplied passphrase. "
            f"Expected error {EXPECTED_ERROR!r}, got: {payload}",
            file=sys.stderr,
        )
        return 1

    message = payload.get("message", "")
    if "never store it" not in message:
        print(
            f"FAIL: refusal did not explain itself. Got message: {message!r}",
            file=sys.stderr,
        )
        return 1

    print("OK: the server refused a supplied passphrase and explained why.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
