"""Turning distance into an honest travel-time estimate.

Travel time is the most consequential output here - US average distance to obstetric care
is 8.1 miles, but 28.1 miles in a maternity care desert, and closures added an average of
25 minutes. Two estimators:

* :class:`RoadFactorEstimator` - pure arithmetic, always available, applies a documented
  detour factor and speed assumption to a straight-line distance.
* :class:`OsrmEstimator` - real road routing. ⚠️ Never run against a live OSRM server.

Both return a :class:`TravelEstimate` carrying a range and a confidence level: a single
confident-looking number is what this problem cannot support.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from birthpath.geo import Coordinates, haversine_miles


class Confidence(str, Enum):
    """How much to trust a travel estimate."""

    ROUTED = "routed"
    """Computed from a real road network. Still subject to traffic and closures."""

    ESTIMATED = "estimated"
    """Derived from straight-line distance with a detour factor. Treat as a wide range."""


@dataclass(frozen=True)
class TravelEstimate:
    """An estimated journey, with its uncertainty attached rather than hidden.

    Args:
        minutes: Best single estimate, in minutes.
        low_minutes: Optimistic end of the plausible range.
        high_minutes: Pessimistic end of the plausible range.
        miles: Distance used, in miles.
        confidence: How the estimate was produced.
        basis: Plain-language description of the assumptions made.
    """

    minutes: float
    low_minutes: float
    high_minutes: float
    miles: float
    confidence: Confidence
    basis: str

    def as_range_text(self) -> str:
        """Render the estimate the way a person should read it.

        Always a range, never a point: a single figure invites planning the underlying data
        cannot support.

        Returns:
            A human-readable range, e.g. ``"roughly 35-70 minutes"``.
        """
        return f"roughly {round(self.low_minutes)}-{round(self.high_minutes)} minutes"


class RoadFactorEstimator:
    """Estimates driving time from straight-line distance and stated assumptions.

    **The fallback, not the goal** - real routing needs an OSM extract and a running
    service, and BirthPath must still be useful without one. Crude and transparent:
    straight-line miles x a circuity factor, divided by an assumed speed, widened to a
    range. The factor leans pessimistic on purpose, because under-estimating travel time
    is the error that hurts people; over-estimating only makes someone leave earlier.
    """

    RURAL_CIRCUITY_FACTOR = 1.4
    """Multiplier from straight-line to approximate road miles."""

    ASSUMED_AVERAGE_MPH = 45.0
    """Assumed average speed including stops, junctions and slower stretches."""

    SLOW_CASE_MPH = 30.0
    """Night, winter, unpaved or mountainous conditions."""

    FAST_CASE_MPH = 60.0
    """Clear highway running."""

    def estimate(self, origin: Coordinates, destination: Coordinates) -> TravelEstimate:
        """Estimate travel time between two points.

        Args:
            origin: Where the journey starts.
            destination: Where it ends.

        Returns:
            A :class:`TravelEstimate` with ``ESTIMATED`` confidence and a wide range.
        """
        straight_miles = haversine_miles(origin, destination)
        road_miles = straight_miles * self.RURAL_CIRCUITY_FACTOR

        best = road_miles / self.ASSUMED_AVERAGE_MPH * 60
        slow = road_miles / self.SLOW_CASE_MPH * 60
        fast = road_miles / self.FAST_CASE_MPH * 60

        return TravelEstimate(
            minutes=best,
            low_minutes=fast,
            high_minutes=slow,
            miles=road_miles,
            confidence=Confidence.ESTIMATED,
            basis=(
                f"Estimated from straight-line distance ({straight_miles:.1f} miles) using a "
                f"{self.RURAL_CIRCUITY_FACTOR}x rural road factor. NOT a routed journey - "
                f"real roads, weather and time of day can change this a lot. "
                f"Please check the route yourself before you need it."
            ),
        )


class OsrmEstimator:
    """Estimates travel time using a real OSRM routing server.

    .. warning::
        ⚠️ **Never exercised against a live server.** Built from OSRM's documented route API
        shape and tested only against recorded responses. Treat the first live run as work.

    Args:
        base_url: Root of an OSRM HTTP API, e.g. ``http://localhost:5000``.
        client: Anything with a ``get(url)`` returning an object with ``.json()``. Injected
            so tests can supply a stub.
    """

    def __init__(self, base_url: str, client) -> None:
        self._base_url = base_url.rstrip("/")
        self._client = client

    def estimate(self, origin: Coordinates, destination: Coordinates) -> TravelEstimate:
        """Route between two points using OSRM.

        Args:
            origin: Where the journey starts.
            destination: Where it ends.

        Returns:
            A :class:`TravelEstimate` with ``ROUTED`` confidence.

        Raises:
            RoutingUnavailable: If the server could not be reached or returned no route.
                Raised rather than silently falling back: a caller that believes it got a
                routed answer when it got a guess is the failure this module prevents.
        """
        url = (
            f"{self._base_url}/route/v1/driving/"
            f"{origin.longitude},{origin.latitude};"
            f"{destination.longitude},{destination.latitude}?overview=false"
        )
        try:
            payload = self._client.get(url).json()
        except Exception as exc:  # noqa: BLE001 - any transport failure is the same to us
            raise RoutingUnavailable(f"OSRM request failed: {url}") from exc

        routes = payload.get("routes") or []
        if not routes:
            raise RoutingUnavailable(f"OSRM returned no route for {url}")

        seconds = float(routes[0]["duration"])
        meters = float(routes[0]["distance"])
        minutes = seconds / 60

        return TravelEstimate(
            minutes=minutes,
            # Even a routed answer is a range in practice: traffic, weather and seasonal
            # road closures are not in the graph. +/-25% is a judgement, stated openly.
            low_minutes=minutes * 0.75,
            high_minutes=minutes * 1.25,
            miles=meters / 1609.344,
            confidence=Confidence.ROUTED,
            basis=(
                "Routed on the real road network. Does not account for traffic, weather, "
                "seasonal closures or unpaved conditions. Please check the route yourself "
                "before you need it."
            ),
        )


class RoutingUnavailable(RuntimeError):
    """Raised when a routing engine could not produce an answer.

    Not caught-and-defaulted inside :class:`OsrmEstimator`: the caller decides whether to
    fall back, and the resulting :class:`TravelEstimate` then says ``ESTIMATED``, so a
    downgrade is always visible.
    """
