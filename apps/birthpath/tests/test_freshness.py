"""Tests for the staleness model - the most safety-critical logic in BirthPath.

Sending someone in labour to a unit that closed six months ago is a physical harm, and it
is entirely possible because closures outpace dataset refreshes: at least 96 L&D closures
since January 2024, nearly 60% of which removed their county's only birthing facility.
"""

from __future__ import annotations

from datetime import date, timedelta

import pytest

from birthpath.domain import (
    AGEING_WITHIN_DAYS,
    FRESH_WITHIN_DAYS,
    Facility,
    Freshness,
    ObstetricStatus,
)
from birthpath.geo import Coordinates

TODAY = date(2026, 9, 20)


def facility_verified(days_ago: int | None, status=ObstetricStatus.DELIVERS) -> Facility:
    return Facility(
        facility_id="f1", name="Test Hospital", city="Test", state="TX",
        coordinates=Coordinates(31.9973, -102.0779),
        status=status,
        verified_on=None if days_ago is None else TODAY - timedelta(days=days_ago),
        source="test",
    )


class TestFreshnessBands:
    @pytest.mark.parametrize("days_ago,expected", [
        (0, Freshness.FRESH),
        (FRESH_WITHIN_DAYS, Freshness.FRESH),
        (FRESH_WITHIN_DAYS + 1, Freshness.AGEING),
        (AGEING_WITHIN_DAYS, Freshness.AGEING),
        (AGEING_WITHIN_DAYS + 1, Freshness.STALE),
        (900, Freshness.STALE),
    ])
    def test_boundaries_are_exact(self, days_ago, expected):
        assert facility_verified(days_ago).freshness(TODAY) is expected

    def test_never_verified_is_its_own_state(self):
        # Distinct from STALE so the user-facing wording can differ: "imported and never
        # confirmed" and "confirmed, but long ago" are different things to a reader.
        assert facility_verified(None).freshness(TODAY) is Freshness.NEVER_VERIFIED

    def test_only_fresh_and_ageing_are_trustworthy(self):
        assert Freshness.FRESH.is_trustworthy()
        assert Freshness.AGEING.is_trustworthy()
        assert not Freshness.STALE.is_trustworthy()
        assert not Freshness.NEVER_VERIFIED.is_trustworthy()


class TestEffectiveStatus:
    def test_a_stale_delivers_record_is_downgraded_to_unknown(self):
        # THE test. A record saying DELIVERS, confirmed 18 months ago, must NOT present as
        # settled fact. Without this the app is a confident directory of hospitals that may
        # no longer deliver babies.
        stale = facility_verified(600, ObstetricStatus.DELIVERS)

        assert stale.status is ObstetricStatus.DELIVERS
        assert stale.effective_status(TODAY) is ObstetricStatus.UNKNOWN

    def test_a_never_verified_record_is_downgraded(self):
        assert facility_verified(None).effective_status(TODAY) is ObstetricStatus.UNKNOWN

    def test_a_fresh_record_keeps_its_status(self):
        assert facility_verified(10).effective_status(TODAY) is ObstetricStatus.DELIVERS

    def test_an_ageing_record_keeps_its_status(self):
        # Ageing is a caution, not a downgrade - otherwise every record becomes UNKNOWN
        # within three months and the app stops being useful at all.
        assert facility_verified(120).effective_status(TODAY) is ObstetricStatus.DELIVERS

    def test_a_fresh_closure_is_respected(self):
        closed = facility_verified(5, ObstetricStatus.CLOSED_TO_DELIVERIES)

        assert closed.effective_status(TODAY) is ObstetricStatus.CLOSED_TO_DELIVERIES


class TestVerificationNotes:
    def test_stale_note_tells_people_to_call(self):
        note = facility_verified(600).verification_note(TODAY)

        assert "too long ago" in note
        assert "Call before you travel" in note

    def test_never_verified_note_admits_it(self):
        note = facility_verified(None).verification_note(TODAY)

        assert "Not confirmed by us" in note
        assert "Call before you travel" in note

    def test_ageing_note_suggests_a_call_without_alarm(self):
        assert "Worth a quick call" in facility_verified(120).verification_note(TODAY)

    def test_fresh_note_is_plain(self):
        assert "Confirmed" in facility_verified(5).verification_note(TODAY)

    def test_every_note_is_non_empty(self):
        for days in (None, 5, 120, 600):
            assert facility_verified(days).verification_note(TODAY).strip()
