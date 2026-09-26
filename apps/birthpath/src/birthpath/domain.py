"""Facilities, their obstetric status, and how stale that status is.

The greatest risk here is not a routing bug but **stale data**: a facility listed as
delivering that stopped six months ago. Closures outpace federal dataset refreshes - at
least 96 L&D closures since January 2024 (~60% eliminating their county's only birthing
facility), 146 rural hospitals stopping by end-2026, 718 hospitals between 2010 and 2024.

So verification is a first-class property of every record: each facility carries the date
a human last confirmed it, and anything stale is downgraded rather than served as fact.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta
from enum import Enum

from birthpath.geo import Coordinates


class ObstetricStatus(str, Enum):
    """Whether a facility delivers babies."""

    DELIVERS = "delivers"
    """Confirmed to operate a labour and delivery unit."""

    CLOSED_TO_DELIVERIES = "closed_to_deliveries"
    """Confirmed to have stopped, whether or not the hospital itself closed."""

    UNKNOWN = "unknown"
    """Never confirmed, or confirmation too old to rely on.

    Treated as "call ahead", never "probably fine": a false positive means someone
    arriving at a closed unit in labour.
    """


class Freshness(str, Enum):
    """How recently a facility's obstetric status was confirmed by a human."""

    FRESH = "fresh"
    """Verified recently enough to show as confirmed."""

    AGEING = "ageing"
    """Verified, but due for re-checking. Shown with a visible caution."""

    STALE = "stale"
    """Too old to present as fact. Downgraded to ``UNKNOWN`` in planning."""

    NEVER_VERIFIED = "never_verified"
    """Imported from a dataset and never confirmed by a person."""

    def is_trustworthy(self) -> bool:
        """Whether this record may be presented as confirmed.

        Returns:
            True only for :attr:`FRESH` and :attr:`AGEING`.
        """
        return self in (Freshness.FRESH, Freshness.AGEING)


# Verification windows. Deliberately short: L&D closures move faster than federal data.
FRESH_WITHIN_DAYS = 90
AGEING_WITHIN_DAYS = 180


@dataclass(frozen=True)
class Facility:
    """A hospital or birth centre, with its obstetric status and provenance.

    Args:
        facility_id: Stable identifier.
        name: Facility name.
        city: City or town.
        state: Two-letter state code.
        coordinates: Where it is.
        status: Its obstetric status as last recorded.
        verified_on: When a human last confirmed that status, or ``None``.
        source: Where the record came from, shown to users.
        phone: Contact number, so "call ahead" is actionable rather than advice.
    """

    facility_id: str
    name: str
    city: str
    state: str
    coordinates: Coordinates
    status: ObstetricStatus
    verified_on: date | None
    source: str
    phone: str | None = None

    def freshness(self, today: date) -> Freshness:
        """How stale this record's verification is.

        Args:
            today: The date to assess against, injected so tests are deterministic.

        Returns:
            The freshness band.
        """
        if self.verified_on is None:
            return Freshness.NEVER_VERIFIED
        age = today - self.verified_on
        if age <= timedelta(days=FRESH_WITHIN_DAYS):
            return Freshness.FRESH
        if age <= timedelta(days=AGEING_WITHIN_DAYS):
            return Freshness.AGEING
        return Freshness.STALE

    def effective_status(self, today: date) -> ObstetricStatus:
        """The status that may honestly be acted on today.

        A stale or never-verified record reports as :attr:`ObstetricStatus.UNKNOWN`
        whatever it once said. **The most important method in BirthPath** - without it, a
        record confirmed in 2024 would present in 2026 as settled fact.

        Args:
            today: The date to assess against.

        Returns:
            The status to use for planning.
        """
        if not self.freshness(today).is_trustworthy():
            return ObstetricStatus.UNKNOWN
        return self.status

    def verification_note(self, today: date) -> str:
        """A plain-language note about how much to trust this record.

        Args:
            today: The date to assess against.

        Returns:
            Text to display beside the facility.
        """
        freshness = self.freshness(today)
        if freshness is Freshness.NEVER_VERIFIED:
            return ("Not confirmed by us. Imported from a public dataset that may be out of "
                    "date. Call before you travel.")
        if freshness is Freshness.STALE:
            return (f"Last confirmed {self.verified_on}, which is too long ago to rely on. "
                    f"Labour and delivery units have been closing quickly. Call before you "
                    f"travel.")
        if freshness is Freshness.AGEING:
            return f"Last confirmed {self.verified_on}. Worth a quick call to be sure."
        return f"Confirmed {self.verified_on}."
