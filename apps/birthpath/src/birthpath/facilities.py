"""Where facility records come from.

The same fixture/live seam as the other four apps, for the same reasons: this build
environment's egress policy blocks the federal data portals, and even with open network
access a test suite that depends on a third-party download is slow and fragile.

The fixture is a small set of realistic facilities spanning a maternity care desert, with
verification dates deliberately spread across fresh, ageing, stale and never-verified so
the freshness logic is exercised. A live feed will not produce those four states to order.
"""

from __future__ import annotations

from datetime import date
from typing import Protocol

from birthpath.domain import Facility, ObstetricStatus
from birthpath.geo import Coordinates


class FacilitySource(Protocol):
    """Supplies facility records.

    A ``Protocol`` rather than an abstract base class: Python's structural typing means an
    implementation does not need to inherit anything, which keeps a future CMS loader free
    of a dependency on this module.
    """

    def all_facilities(self) -> list[Facility]:
        """Return every known facility."""

    def describe_source(self) -> str:
        """Return a human-readable description of where the data came from."""


class FixtureFacilitySource:
    """A small, deterministic set of facilities for development and tests.

    Coordinates are real west Texas towns, because that region is a documented extreme:
    **76 of 107 west Texas counties are maternal healthcare deserts.** The facilities
    themselves are illustrative, not a real directory - see :meth:`describe_source`.
    """

    def all_facilities(self) -> list[Facility]:
        """Return the fixture facilities.

        The set is built to exercise every branch of the freshness and status logic:

        * one fresh, delivering facility (the expected happy path)
        * one ageing but delivering (shown with a caution)
        * one **stale** record that still says ``DELIVERS`` - which must be downgraded
        * one never-verified import
        * one confirmed closed, which must never be recommended

        Returns:
            The facility list.
        """
        return [
            Facility(
                facility_id="tx-mid-001",
                name="Midland Regional Medical Center",
                city="Midland", state="TX",
                coordinates=Coordinates(31.9973, -102.0779),
                status=ObstetricStatus.DELIVERS,
                verified_on=date(2026, 8, 15),
                source="Illustrative sample record",
                phone="+1-555-0100",
            ),
            Facility(
                facility_id="tx-odessa-002",
                name="Odessa County Hospital",
                city="Odessa", state="TX",
                coordinates=Coordinates(31.8457, -102.3676),
                status=ObstetricStatus.DELIVERS,
                verified_on=date(2026, 5, 1),
                source="Illustrative sample record",
                phone="+1-555-0101",
            ),
            Facility(
                facility_id="tx-bigspring-003",
                name="Big Spring Community Hospital",
                city="Big Spring", state="TX",
                coordinates=Coordinates(32.2504, -101.4787),
                # Says it delivers, but was last confirmed long ago. THE dangerous case.
                status=ObstetricStatus.DELIVERS,
                verified_on=date(2025, 1, 10),
                source="Illustrative sample record",
                phone="+1-555-0102",
            ),
            Facility(
                facility_id="tx-pecos-004",
                name="Pecos Valley Hospital",
                city="Pecos", state="TX",
                coordinates=Coordinates(31.4229, -103.4932),
                status=ObstetricStatus.DELIVERS,
                verified_on=None,
                source="Illustrative sample record (never confirmed)",
                phone="+1-555-0103",
            ),
            Facility(
                facility_id="tx-monahans-005",
                name="Monahans District Hospital",
                city="Monahans", state="TX",
                coordinates=Coordinates(31.5943, -102.8927),
                status=ObstetricStatus.CLOSED_TO_DELIVERIES,
                verified_on=date(2026, 9, 1),
                source="Illustrative sample record",
                phone="+1-555-0104",
            ),
        ]

    def describe_source(self) -> str:
        """Describe this source, making its non-live nature unmissable.

        Returns:
            A label shown on every plan built from this source.
        """
        return (
            "ILLUSTRATIVE SAMPLE DATA - NOT A REAL FACILITY DIRECTORY. "
            "Coordinates are real towns; the facilities and their statuses are invented to "
            "exercise the software. Never use this to plan a real birth."
        )
