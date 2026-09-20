"""Geographic distance maths.

This module holds exactly one dangerous idea, so it is worth isolating: **straight-line
distance is not travel time, and in rural America the gap is not small.**

Great-circle distance ignores rivers, mountains, lakes, private land and the simple fact
that rural road networks are sparse. Its error is not random - it is *systematically*
biased in the dangerous direction, because roads are always at least as long as the
straight line and usually much longer. A tool that reported "12 miles" for a journey that
actually takes 50 minutes of switchbacks would be worse than no tool, because the number
looks authoritative.

So `haversine_miles` exists to answer "which facilities are worth considering at all",
and nothing else. Converting distance into time is the job of
:mod:`birthpath.travel`, which is explicit about how much it is guessing.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

EARTH_RADIUS_MILES = 3958.7613


@dataclass(frozen=True)
class Coordinates:
    """A latitude/longitude pair.

    Frozen because a coordinate that changes under you is a bug that surfaces as a
    mysteriously wrong distance much later.

    Args:
        latitude: Degrees north, -90 to 90.
        longitude: Degrees east, -180 to 180.

    Raises:
        ValueError: If either value is outside its valid range. Failing loudly beats
            silently computing a distance to a point that cannot exist.
    """

    latitude: float
    longitude: float

    def __post_init__(self) -> None:
        if not -90 <= self.latitude <= 90:
            raise ValueError(f"latitude must be between -90 and 90, got {self.latitude}")
        if not -180 <= self.longitude <= 180:
            raise ValueError(f"longitude must be between -180 and 180, got {self.longitude}")


def haversine_miles(origin: Coordinates, destination: Coordinates) -> float:
    """Great-circle distance between two points, in miles.

    **This is not a travel distance and must never be shown to a user as one.** It is a
    screening measure: cheap enough to run against thousands of facilities to decide which
    handful deserve a real routing lookup.

    Args:
        origin: Starting point.
        destination: Ending point.

    Returns:
        Distance in miles, always non-negative.
    """
    lat1, lon1 = math.radians(origin.latitude), math.radians(origin.longitude)
    lat2, lon2 = math.radians(destination.latitude), math.radians(destination.longitude)

    dlat = lat2 - lat1
    dlon = lon2 - lon1

    a = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS_MILES * math.asin(math.sqrt(a))
