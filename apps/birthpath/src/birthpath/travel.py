"""Turning distance into an honest travel-time estimate.

The whole reason BirthPath exists is a time number:

    US average distance to obstetric care: **8.1 miles**.
    For women in a maternity care desert: **28.1 miles**.
    Closures added an average of **25 minutes** of travel time.

Twenty-five extra minutes sounds survivable until it is attached to a postpartum
haemorrhage, a placental abruption, or a precipitous labour on an icy road at 2am. So the
travel-time estimate is the most consequential output in this application - and the one
most likely to be quietly, dangerously wrong.

Two estimators are provided:

* :class:`RoadFactorEstimator` - pure arithmetic, no dependencies, always available. It
  applies a documented detour factor and speed assumption to a straight-line distance and
  is *explicit* that it is an estimate with a wide range.
* :class:`OsrmEstimator` - real road routing via an OSRM server. Correct, and unverified:
  no OSRM instance was reachable from the environment this was written in.

Both return a :class:`TravelEstimate` carrying an explicit range and a confidence level,
because a single confident-looking number is exactly what this problem cannot support.
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

        Always a range, never a point. A single figure invites planning that the underlying
        data cannot support, and on this problem over-confidence has a body count.

        Returns:
            A human-readable range, e.g. ``"roughly 35-70 minutes"``.
        """
        return f"roughly {round(self.low_minutes)}-{round(self.high_minutes)} minutes"


class RoadFactorEstimator:
    """Estimates driving time from straight-line distance and stated assumptions.

    **This is the fallback, not the goal.** It exists because a real routing engine needs
    an OSM extract and a running service, and BirthPath must still say something useful on
    a laptop with nothing installed.

    The method is deliberately crude and deliberately transparent: multiply the
    straight-line distance by a *circuity factor* to approximate road distance, then divide
    by an assumed average speed, then widen the result into a range.

    Circuity in rural America is genuinely high - sparse grids, rivers, terrain - and the
    factor here leans pessimistic on purpose. Under-estimating travel time on this problem
    is the error that hurts people; over-estimating it only makes someone leave earlier.
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
        **Never exercised against a live server.** No OSRM instance was reachable from the
        environment this was written in, so this class is built from OSRM's documented
        route API shape and tested only against recorded responses. Treat the first live run
        as part of the work.

    Args:
        base_url: Root of an OSRM HTTP API, e.g. ``http://localhost:5000``.
        client: Anything with a ``get(url)`` returning an object with ``.json()``. Injected
            so tests can supply a stub and the class stays usable without a network.
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
                Raised rather than silently falling back, because a caller that believes it
                got a routed answer when it got a guess is exactly the failure this module
                is structured to prevent.
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

    Deliberately not caught-and-defaulted inside :class:`OsrmEstimator`. The caller decides
    whether to fall back to an estimate, and when it does, the resulting
    :class:`TravelEstimate` says ``ESTIMATED`` - so a downgrade is always visible rather
    than silent.
    """
