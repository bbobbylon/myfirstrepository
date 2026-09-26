"""Tests for distance and travel-time estimation.

The central assertion in this file is that **BirthPath never presents straight-line
distance as travel time**. That error is systematically biased in the dangerous direction:
roads are always at least as long as the straight line, so an unadjusted figure always
*understates* how long someone will be in a car.
"""

from __future__ import annotations

import math

import pytest

from birthpath.geo import Coordinates, haversine_miles
from birthpath.travel import (
    Confidence,
    OsrmEstimator,
    RoadFactorEstimator,
    RoutingUnavailable,
)

MIDLAND = Coordinates(31.9973, -102.0779)
ODESSA = Coordinates(31.8457, -102.3676)


class TestCoordinates:
    def test_rejects_impossible_latitude(self):
        with pytest.raises(ValueError, match="latitude"):
            Coordinates(91.0, 0.0)

    def test_rejects_impossible_longitude(self):
        with pytest.raises(ValueError, match="longitude"):
            Coordinates(0.0, 181.0)

    @pytest.mark.parametrize("lat,lon", [(90, 180), (-90, -180), (0, 0)])
    def test_accepts_boundaries(self, lat, lon):
        assert Coordinates(lat, lon) is not None

    def test_is_immutable(self):
        # A coordinate that changes under you surfaces much later as a wrong distance.
        with pytest.raises(Exception):
            MIDLAND.latitude = 0.0  # type: ignore[misc]


class TestHaversine:
    def test_zero_distance_to_itself(self):
        assert haversine_miles(MIDLAND, MIDLAND) == pytest.approx(0.0, abs=1e-9)

    def test_symmetric(self):
        assert haversine_miles(MIDLAND, ODESSA) == pytest.approx(
            haversine_miles(ODESSA, MIDLAND))

    def test_known_distance_midland_to_odessa(self):
        # Midland and Odessa are about 19-20 miles apart in a straight line.
        assert haversine_miles(MIDLAND, ODESSA) == pytest.approx(19.5, abs=1.5)

    def test_never_negative(self):
        assert haversine_miles(Coordinates(-45, -170), Coordinates(60, 175)) > 0


class TestRoadFactorEstimator:
    def setup_method(self):
        self.estimator = RoadFactorEstimator()

    def test_road_miles_always_exceed_straight_line(self):
        # THE safety property. Roads are never shorter than the great circle, so an
        # estimate that came out lower would be understating travel in the one direction
        # that hurts people.
        straight = haversine_miles(MIDLAND, ODESSA)
        estimate = self.estimator.estimate(MIDLAND, ODESSA)

        assert estimate.miles > straight

    def test_reports_a_range_not_a_point(self):
        estimate = self.estimator.estimate(MIDLAND, ODESSA)

        assert estimate.low_minutes < estimate.minutes < estimate.high_minutes

    def test_range_text_is_a_range(self):
        # A single confident number invites planning the data cannot support.
        text = self.estimator.estimate(MIDLAND, ODESSA).as_range_text()

        assert "-" in text and "roughly" in text

    def test_declares_itself_an_estimate(self):
        estimate = self.estimator.estimate(MIDLAND, ODESSA)

        assert estimate.confidence is Confidence.ESTIMATED
        assert "NOT a routed journey" in estimate.basis

    def test_longer_distances_take_longer(self):
        near = self.estimator.estimate(MIDLAND, ODESSA)
        far = self.estimator.estimate(MIDLAND, Coordinates(29.7604, -95.3698))  # Houston

        assert far.minutes > near.minutes

    def test_zero_distance_is_zero_time(self):
        assert self.estimator.estimate(MIDLAND, MIDLAND).minutes == pytest.approx(0.0)


class _StubResponse:
    def __init__(self, payload):
        self._payload = payload

    def json(self):
        return self._payload


class _StubClient:
    def __init__(self, payload=None, raises=False):
        self._payload = payload
        self._raises = raises
        self.last_url = None

    def get(self, url):
        self.last_url = url
        if self._raises:
            raise ConnectionError("no route to host")
        return _StubResponse(self._payload)


class TestOsrmEstimator:
    """Exercised against recorded shapes only - no OSRM server was reachable."""

    def test_parses_a_routed_response(self):
        client = _StubClient({"routes": [{"duration": 2820.0, "distance": 62764.0}]})
        estimate = OsrmEstimator("http://osrm:5000", client).estimate(MIDLAND, ODESSA)

        assert estimate.confidence is Confidence.ROUTED
        assert estimate.minutes == pytest.approx(47.0)
        assert estimate.miles == pytest.approx(39.0, abs=0.5)

    def test_builds_lon_lat_url_not_lat_lon(self):
        # OSRM takes longitude FIRST. Getting this backwards silently routes between two
        # entirely different places, which is the worst kind of bug: plausible output.
        client = _StubClient({"routes": [{"duration": 60.0, "distance": 1000.0}]})
        OsrmEstimator("http://osrm:5000", client).estimate(MIDLAND, ODESSA)

        assert f"{MIDLAND.longitude},{MIDLAND.latitude}" in client.last_url

    def test_even_a_routed_answer_carries_a_range(self):
        client = _StubClient({"routes": [{"duration": 2820.0, "distance": 62764.0}]})
        estimate = OsrmEstimator("http://osrm:5000", client).estimate(MIDLAND, ODESSA)

        # Traffic, weather and seasonal closures are not in the graph.
        assert estimate.low_minutes < estimate.minutes < estimate.high_minutes

    def test_raises_rather_than_silently_guessing_on_transport_failure(self):
        # A caller that believes it got a routed answer when it got a guess is exactly the
        # failure this module is structured to prevent.
        with pytest.raises(RoutingUnavailable):
            OsrmEstimator("http://osrm:5000", _StubClient(raises=True)).estimate(
                MIDLAND, ODESSA)

    def test_raises_when_no_route_exists(self):
        with pytest.raises(RoutingUnavailable, match="no route"):
            OsrmEstimator("http://osrm:5000", _StubClient({"routes": []})).estimate(
                MIDLAND, ODESSA)
