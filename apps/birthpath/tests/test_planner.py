"""Tests for the plan builder, including the line it must never cross.

BirthPath gives travel logistics. It must never imply a clinical judgement - never "you
have time", never "this is safe". Several assertions here check *wording*, because that
line is drawn by the sentences the app emits, not by a disclaimer somewhere else.
"""

from __future__ import annotations

from datetime import date

from birthpath.domain import Facility, ObstetricStatus
from birthpath.facilities import FixtureFacilitySource
from birthpath.geo import Coordinates
from birthpath.planner import BirthPlanBuilder
from birthpath.travel import RoadFactorEstimator

TODAY = date(2026, 9, 20)
NEAR_MIDLAND = Coordinates(32.0, -102.1)


def builder(source=None) -> BirthPlanBuilder:
    return BirthPlanBuilder(source or FixtureFacilitySource(), RoadFactorEstimator())


class _EmptySource:
    def all_facilities(self):
        return []

    def describe_source(self):
        return "empty test source"


class _OnlyStaleSource:
    """Every facility claims to deliver but none has been confirmed recently."""

    def all_facilities(self):
        return [
            Facility("s1", "Stale General", "Nowhere", "TX",
                     Coordinates(31.5, -102.5), ObstetricStatus.DELIVERS,
                     date(2024, 1, 1), "test"),
            Facility("s2", "Never Checked", "Nowhere", "TX",
                     Coordinates(31.6, -102.6), ObstetricStatus.DELIVERS, None, "test"),
        ]

    def describe_source(self):
        return "stale test source"


class TestRanking:
    def test_picks_the_nearest_confirmed_facility_as_primary(self):
        plan = builder().build(NEAR_MIDLAND, TODAY)

        assert plan.primary is not None
        assert plan.primary.facility.name == "Midland Regional Medical Center"

    def test_provides_a_backup(self):
        plan = builder().build(NEAR_MIDLAND, TODAY)

        assert plan.backup is not None
        assert plan.backup.facility.name != plan.primary.facility.name

    def test_backup_is_further_than_primary(self):
        plan = builder().build(NEAR_MIDLAND, TODAY)

        assert plan.backup.travel.minutes >= plan.primary.travel.minutes

    def test_ranks_by_travel_time_not_straight_line(self):
        plan = builder().build(NEAR_MIDLAND, TODAY)
        times = [p.travel.minutes for p in plan.all_considered]

        assert times == sorted(times)

    def test_never_recommends_a_confirmed_closed_unit(self):
        # Monahans is confirmed CLOSED_TO_DELIVERIES. It must appear in the full list for
        # transparency but must never be primary or backup.
        plan = builder().build(NEAR_MIDLAND, TODAY)
        chosen = {p.facility.name for p in (plan.primary, plan.backup) if p}

        assert "Monahans District Hospital" not in chosen
        assert any(p.facility.name == "Monahans District Hospital"
                   for p in plan.all_considered)


class TestStaleDataHandling:
    def test_a_stale_delivers_record_is_not_chosen_as_primary(self):
        # Big Spring says DELIVERS but was last confirmed in January 2025. Choosing it
        # would be the exact failure this application is built to avoid.
        plan = builder().build(NEAR_MIDLAND, TODAY)
        chosen = {p.facility.name for p in (plan.primary, plan.backup) if p}

        assert "Big Spring Community Hospital" not in chosen

    def test_unconfirmed_facilities_are_surfaced_not_hidden(self):
        # They may well be the best option - we just cannot say so, and one phone call the
        # user can make (and we cannot) would settle it.
        plan = builder().build(Coordinates(31.5, -102.9), TODAY)

        assert plan.all_considered
        assert any(p.effective_status is ObstetricStatus.UNKNOWN
                   for p in plan.all_considered)

    def test_everything_unconfirmed_yields_no_primary_and_says_so(self):
        plan = builder(_OnlyStaleSource()).build(NEAR_MIDLAND, TODAY)

        assert plan.primary is None
        assert not plan.has_confirmed_option()
        assert any("could NOT confirm any facility" in w for w in plan.warnings)

    def test_no_facilities_at_all_is_handled(self):
        plan = builder(_EmptySource()).build(NEAR_MIDLAND, TODAY)

        assert plan.primary is None
        assert plan.all_considered == []
        assert plan.warnings

    def test_non_fresh_records_demand_a_call_ahead(self):
        plan = builder().build(NEAR_MIDLAND, TODAY)

        for planned in plan.all_considered:
            if planned.freshness.value != "fresh":
                assert planned.must_call_ahead


class TestWarnings:
    def test_every_plan_says_call_ahead_or_could_not_confirm(self):
        plan = builder().build(NEAR_MIDLAND, TODAY)

        assert any("CALL AHEAD" in w for w in plan.warnings)

    def test_every_plan_states_it_is_not_medical_advice(self):
        # The bright line, restated on every single plan rather than buried once in an
        # onboarding screen.
        plan = builder().build(NEAR_MIDLAND, TODAY)

        assert any("does NOT give medical advice" in w for w in plan.warnings)

    def test_every_plan_tells_people_to_print_it(self):
        # Mobile coverage is unreliable in exactly the places this matters most.
        plan = builder().build(NEAR_MIDLAND, TODAY)

        assert any("Print this plan" in w for w in plan.warnings)

    def test_a_single_option_is_flagged_as_having_no_backup(self):
        class _OneOnly:
            def all_facilities(self):
                return [Facility("o1", "Only Hospital", "Town", "TX",
                                 Coordinates(31.9, -102.0), ObstetricStatus.DELIVERS,
                                 TODAY, "test")]

            def describe_source(self):
                return "single facility source"

        plan = builder(_OneOnly()).build(NEAR_MIDLAND, TODAY)

        assert plan.backup is None
        assert any("no backup" in w for w in plan.warnings)

    def test_plan_NEVER_says_you_have_time_or_that_it_is_safe(self):
        # Distance is a fact. Whether a distance is acceptable for a specific pregnancy is
        # a clinical judgement belonging to a clinician who knows that patient.
        plan = builder().build(NEAR_MIDLAND, TODAY)
        text = " ".join(plan.warnings).lower()
        for planned in plan.all_considered:
            text += " " + planned.verification_note.lower()
            text += " " + planned.travel.basis.lower()

        assert "you have time" not in text
        assert "this is safe" not in text
        assert "safe distance" not in text
        assert "low risk" not in text


class TestProvenance:
    def test_the_plan_carries_its_data_source(self):
        plan = builder().build(NEAR_MIDLAND, TODAY)

        assert "NOT A REAL FACILITY DIRECTORY" in plan.source_note

    def test_fixture_source_is_unmistakably_labelled(self):
        note = FixtureFacilitySource().describe_source()

        assert "ILLUSTRATIVE SAMPLE DATA" in note
        assert "Never use this to plan a real birth" in note
