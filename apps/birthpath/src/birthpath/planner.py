"""The engine: turn an address into a ranked, honest birth logistics plan.

BirthPath is a **logistics planner, not a source of medical advice.** The line it must
never cross: it says *"this hospital is 47 minutes away by road"*; it must **never** say
*"you have time"* or *"this is safe"*. Distance is a fact; whether a distance is
acceptable for a specific pregnancy is a clinician's judgement.

Scale of the problem: one in three US counties are maternity care deserts, holding 5.8
million women and 358,000 infants; average distance to obstetric care is 8.1 miles
nationally but 28.1 miles in a desert.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date

from birthpath.domain import Facility, Freshness, ObstetricStatus
from birthpath.geo import Coordinates
from birthpath.travel import TravelEstimate


@dataclass(frozen=True)
class PlannedFacility:
    """One facility as it appears on a plan.

    Args:
        facility: The underlying record.
        travel: Estimated journey from the user's location.
        effective_status: Status after freshness downgrading.
        freshness: How stale the record is.
        verification_note: Plain-language trust note.
        must_call_ahead: Whether the plan insists on a phone call first.
    """

    facility: Facility
    travel: TravelEstimate
    effective_status: ObstetricStatus
    freshness: Freshness
    verification_note: str
    must_call_ahead: bool


@dataclass(frozen=True)
class BirthPlan:
    """A complete, printable logistics plan.

    Args:
        origin: Where the plan was built from.
        built_on: The date it was built.
        source_note: Provenance of the facility data.
        primary: The nearest facility believed to deliver, if any.
        backup: The next one, if any.
        unverified_nearby: Closer facilities that could not be confirmed.
        all_considered: Everything ranked, for transparency.
        warnings: Standing safety notes, always present.
    """

    origin: Coordinates
    built_on: date
    source_note: str
    primary: PlannedFacility | None
    backup: PlannedFacility | None
    unverified_nearby: list[PlannedFacility] = field(default_factory=list)
    all_considered: list[PlannedFacility] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def has_confirmed_option(self) -> bool:
        """Whether any facility could be confirmed as delivering.

        Returns:
            True if a primary was found.
        """
        return self.primary is not None


class BirthPlanBuilder:
    """Builds a :class:`BirthPlan` from a location and a facility source.

    Args:
        facility_source: Where facility records come from.
        travel_estimator: Anything with ``estimate(origin, destination)``.
    """

    def __init__(self, facility_source, travel_estimator) -> None:
        self._facilities = facility_source
        self._travel = travel_estimator

    def build(self, origin: Coordinates, today: date) -> BirthPlan:
        """Build a plan for a location.

        Ranking is by **estimated travel time**, not straight-line distance: in rural
        areas those orders genuinely differ.

        Args:
            origin: The user's location.
            today: The date to assess freshness against.

        Returns:
            A complete plan, including when nothing could be confirmed.
        """
        planned = [self._plan_one(facility, origin, today)
                   for facility in self._facilities.all_facilities()]
        planned.sort(key=lambda p: p.travel.minutes)

        delivering = [p for p in planned
                      if p.effective_status is ObstetricStatus.DELIVERS]
        unknown = [p for p in planned
                   if p.effective_status is ObstetricStatus.UNKNOWN]

        primary = delivering[0] if delivering else None
        backup = delivering[1] if len(delivering) > 1 else None

        # Unverified facilities that are CLOSER than the confirmed primary are surfaced
        # separately rather than hidden. They may well be the best option - we simply
        # cannot say so, and the user can settle it with one phone call we cannot make.
        if primary is not None:
            closer_unknown = [p for p in unknown if p.travel.minutes < primary.travel.minutes]
        else:
            closer_unknown = unknown

        return BirthPlan(
            origin=origin,
            built_on=today,
            source_note=self._facilities.describe_source(),
            primary=primary,
            backup=backup,
            unverified_nearby=closer_unknown,
            all_considered=planned,
            warnings=self._warnings(primary, backup, closer_unknown),
        )

    def _plan_one(self, facility: Facility, origin: Coordinates, today: date) -> PlannedFacility:
        """Assess one facility against the user's location.

        Args:
            facility: The record to assess.
            origin: The user's location.
            today: The date to assess freshness against.

        Returns:
            The planned facility.
        """
        freshness = facility.freshness(today)
        effective = facility.effective_status(today)

        return PlannedFacility(
            facility=facility,
            travel=self._travel.estimate(origin, facility.coordinates),
            effective_status=effective,
            freshness=freshness,
            verification_note=facility.verification_note(today),
            # Always true unless we have a fresh confirmation. Calling costs a minute;
            # arriving at a closed unit in labour does not have a comparable cost.
            must_call_ahead=freshness is not Freshness.FRESH,
        )

    def _warnings(self, primary, backup, closer_unknown) -> list[str]:
        """Assemble the standing safety notes shown on every plan.

        Ordered most-important-first: this may be read on a phone with one bar by someone
        who reads only the top.

        Args:
            primary: The chosen primary facility, if any.
            backup: The chosen backup, if any.
            closer_unknown: Unconfirmed facilities nearer than the primary.

        Returns:
            The warnings.
        """
        warnings: list[str] = []

        if primary is None:
            warnings.append(
                "We could NOT confirm any facility near you that delivers babies. That does "
                "not mean there is none - it means our data cannot confirm one. Ask your "
                "prenatal provider, and call the hospitals listed below."
            )
        else:
            warnings.append(
                "CALL AHEAD before you travel, every time. Labour and delivery units have "
                "been closing quickly, and a unit that was open last month may not be open "
                "today."
            )

        if backup is None and primary is not None:
            warnings.append(
                "We found only ONE facility we could confirm. You have no backup in this "
                "plan. Please talk to your provider about a second option."
            )

        if closer_unknown:
            warnings.append(
                f"{len(closer_unknown)} facility(ies) closer to you could not be confirmed. "
                f"They may be better options - one phone call would settle it."
            )

        # The bright line, restated on every single plan.
        warnings.append(
            "BirthPath gives travel logistics only. It does NOT give medical advice and "
            "cannot tell you whether a distance is safe for your pregnancy. That is a "
            "conversation for your midwife or doctor."
        )
        warnings.append(
            "Print this plan or write it down. Mobile coverage is unreliable in exactly the "
            "places this matters most."
        )
        return warnings
