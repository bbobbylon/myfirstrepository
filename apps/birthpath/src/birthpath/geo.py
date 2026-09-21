"""Geographic distance maths.

**Straight-line distance is not travel time**, and its error is not random: roads are
always at least as long as the straight line, so the bias always runs in the dangerous
direction. `haversine_miles` therefore answers only "which facilities are worth
considering at all". Converting distance to time belongs to :mod:`birthpath.travel`.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

EARTH_RADIUS_MILES = 3958.7613


@dataclass(frozen=True)
class Coordinates:
    """A latitude/longitude pair. Frozen: a coordinate that mutates surfaces as a
    mysteriously wrong distance much later.

    Args:
        latitude: Degrees north, -90 to 90.
        longitude: Degrees east, -180 to 180.

    Raises:
        ValueError: If either value is outside its valid range.
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

    **Not a travel distance; never show it as one.** It is a screening measure, cheap
    enough to run against thousands of facilities to pick the handful worth routing.

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
