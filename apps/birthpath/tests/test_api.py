"""End-to-end tests of the HTTP API.

Unit tests can all pass while the app fails to start; these prove the pieces are wired.
"""

from __future__ import annotations

from fastapi.testclient import TestClient

from birthpath.main import app

client = TestClient(app)


class TestPlanEndpoint:
    def test_builds_a_plan(self):
        response = client.get("/api/plan",
                              params={"latitude": 32.0, "longitude": -102.1,
                                      "on": "2026-09-20"})
        assert response.status_code == 200

        plan = response.json()
        assert plan["hasConfirmedOption"] is True
        assert plan["primary"]["name"] == "Midland Regional Medical Center"
        assert plan["primary"]["travel"]["rangeText"].startswith("roughly")

    def test_plan_carries_its_warnings_and_provenance(self):
        plan = client.get("/api/plan",
                          params={"latitude": 32.0, "longitude": -102.1,
                                  "on": "2026-09-20"}).json()

        warnings = " ".join(plan["warnings"])
        assert "CALL AHEAD" in warnings
        assert "does NOT give medical advice" in warnings
        assert "NOT A REAL FACILITY DIRECTORY" in plan["dataSource"]

    def test_travel_is_a_range_not_a_bare_number(self):
        plan = client.get("/api/plan",
                          params={"latitude": 32.0, "longitude": -102.1}).json()

        assert "-" in plan["primary"]["travel"]["rangeText"]
        assert plan["primary"]["travel"]["confidence"] == "estimated"

    def test_rejects_impossible_coordinates(self):
        assert client.get("/api/plan",
                          params={"latitude": 999, "longitude": 0}).status_code == 422

    def test_freshness_is_assessed_against_the_supplied_date(self):
        # Far in the future, every record has gone stale - so nothing can be confirmed.
        plan = client.get("/api/plan",
                          params={"latitude": 32.0, "longitude": -102.1,
                                  "on": "2030-01-01"}).json()

        assert plan["hasConfirmedOption"] is False
        assert any("could NOT confirm" in w for w in plan["warnings"])


class TestFacilitiesEndpoint:
    def test_lists_facilities_with_their_verification_state(self):
        body = client.get("/api/facilities", params={"on": "2026-09-20"}).json()

        assert len(body["facilities"]) == 5
        assert "ILLUSTRATIVE SAMPLE DATA" in body["dataSource"]

    def test_shows_which_records_were_downgraded(self):
        # Inspectable rather than taken on faith: a user, journalist or health department
        # should be able to see exactly what we are and are not vouching for.
        body = client.get("/api/facilities", params={"on": "2026-09-20"}).json()
        downgraded = [f for f in body["facilities"] if f["downgraded"]]

        names = {f["name"] for f in downgraded}
        assert "Big Spring Community Hospital" in names   # stale DELIVERS
        assert "Pecos Valley Hospital" in names           # never verified

    def test_a_downgraded_record_keeps_its_recorded_status_visible(self):
        body = client.get("/api/facilities", params={"on": "2026-09-20"}).json()
        big_spring = next(f for f in body["facilities"]
                          if f["name"] == "Big Spring Community Hospital")

        assert big_spring["recordedStatus"] == "delivers"
        assert big_spring["effectiveStatus"] == "unknown"


class TestHealthEndpoint:
    def test_reports_service_and_thresholds(self):
        body = client.get("/api/health").json()

        assert body["service"] == "birthpath"
        assert body["freshWithinDays"] == 90
        assert "Not medical advice" in body["notice"]
