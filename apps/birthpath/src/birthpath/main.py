"""HTTP API for BirthPath.

FastAPI rather than Django, which is a deviation from the original proposal worth
explaining rather than glossing over.

The proposal recommended Django + PostGIS, and the *Python* half of that reasoning fully
holds: this is data-wrangling and geospatial work where Python's ecosystem is decisively
better than the JVM's. The *Django* half was justified by its admin, auth and ORM - all of
which need a database. This environment has no Docker daemon, so PostGIS cannot run here,
and a Django app configured against a database nobody can start would be a worse artefact
than a working one without it.

So v0.1 is FastAPI with in-memory fixtures: it runs anywhere Python does, and the
geospatial logic - the part that actually needed Python - is fully exercised. PostGIS
becomes worthwhile at the point there are thousands of facilities to index, which is a
v0.2 concern.
"""

from __future__ import annotations

from datetime import date

from fastapi import FastAPI, HTTPException, Query

from birthpath.domain import Freshness
from birthpath.facilities import FixtureFacilitySource
from birthpath.geo import Coordinates
from birthpath.planner import BirthPlan, BirthPlanBuilder, PlannedFacility
from birthpath.travel import RoadFactorEstimator

app = FastAPI(
    title="BirthPath",
    version="0.1.0",
    description=(
        "Travel logistics for maternity care deserts. Gives distances and travel times "
        "only - never medical advice, and never an assessment of whether a distance is "
        "safe for a given pregnancy."
    ),
)

_source = FixtureFacilitySource()
_builder = BirthPlanBuilder(_source, RoadFactorEstimator())


def _render_facility(planned: PlannedFacility) -> dict:
    """Flatten a planned facility for JSON.

    Args:
        planned: The facility to render.

    Returns:
        A JSON-ready dict.
    """
    return {
        "name": planned.facility.name,
        "city": planned.facility.city,
        "state": planned.facility.state,
        "phone": planned.facility.phone,
        "status": planned.effective_status.value,
        "recordedStatus": planned.facility.status.value,
        "freshness": planned.freshness.value,
        "verifiedOn": (planned.facility.verified_on.isoformat()
                       if planned.facility.verified_on else None),
        "verificationNote": planned.verification_note,
        "mustCallAhead": planned.must_call_ahead,
        "travel": {
            "estimateMinutes": round(planned.travel.minutes),
            "rangeText": planned.travel.as_range_text(),
            "roadMiles": round(planned.travel.miles, 1),
            "confidence": planned.travel.confidence.value,
            "basis": planned.travel.basis,
        },
    }


def _render_plan(plan: BirthPlan) -> dict:
    """Flatten a whole plan for JSON.

    Args:
        plan: The plan to render.

    Returns:
        A JSON-ready dict.
    """
    return {
        "builtOn": plan.built_on.isoformat(),
        "origin": {"latitude": plan.origin.latitude, "longitude": plan.origin.longitude},
        "dataSource": plan.source_note,
        "hasConfirmedOption": plan.has_confirmed_option(),
        "primary": _render_facility(plan.primary) if plan.primary else None,
        "backup": _render_facility(plan.backup) if plan.backup else None,
        "unverifiedNearby": [_render_facility(p) for p in plan.unverified_nearby],
        "allConsidered": [_render_facility(p) for p in plan.all_considered],
        "warnings": plan.warnings,
    }


@app.get("/api/plan")
def build_plan(
    latitude: float = Query(..., ge=-90, le=90, description="Your latitude"),
    longitude: float = Query(..., ge=-180, le=180, description="Your longitude"),
    on: date | None = Query(None, description="Date to assess data freshness against"),
) -> dict:
    """Build a birth logistics plan for a location.

    Args:
        latitude: Where the person is.
        longitude: Where the person is.
        on: Optional override for "today", so freshness behaviour is demonstrable.

    Returns:
        The rendered plan.

    Raises:
        HTTPException: 422 if the coordinates are invalid.
    """
    try:
        origin = Coordinates(latitude, longitude)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    return _render_plan(_builder.build(origin, on or date.today()))


@app.get("/api/facilities")
def list_facilities(on: date | None = Query(None)) -> dict:
    """List every known facility with its verification state.

    Exists so the freshness model is inspectable. A user - or a journalist, or a health
    department - should be able to see exactly which records we consider trustworthy and
    which we are downgrading, rather than taking the ranking on faith.

    Args:
        on: Optional override for "today".

    Returns:
        The facilities and the data source note.
    """
    today = on or date.today()
    return {
        "dataSource": _source.describe_source(),
        "assessedOn": today.isoformat(),
        "facilities": [
            {
                "name": f.name,
                "city": f.city,
                "state": f.state,
                "recordedStatus": f.status.value,
                "effectiveStatus": f.effective_status(today).value,
                "freshness": f.freshness(today).value,
                "downgraded": f.effective_status(today) != f.status,
                "verificationNote": f.verification_note(today),
            }
            for f in _source.all_facilities()
        ],
    }


@app.get("/api/health")
def health() -> dict:
    """Liveness probe used by the container smoke test.

    Returns:
        Service name, version and the freshness thresholds in force.
    """
    from birthpath.domain import AGEING_WITHIN_DAYS, FRESH_WITHIN_DAYS

    return {
        "service": "birthpath",
        "version": "0.1.0",
        "freshWithinDays": FRESH_WITHIN_DAYS,
        "ageingWithinDays": AGEING_WITHIN_DAYS,
        "notice": "Travel logistics only. Not medical advice.",
    }
