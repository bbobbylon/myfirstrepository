package com.shadeclock.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shadeclock.crew.Crew;
import com.shadeclock.crew.Worker;
import com.shadeclock.forecast.ForecastSource;
import com.shadeclock.heat.HeatIndexCalculator;
import com.shadeclock.heat.HeatRiskBand;
import com.shadeclock.heat.HourlyConditions;
import com.shadeclock.rules.RulesetRegistry;
import com.shadeclock.schedule.ScheduleBuilder;
import com.shadeclock.schedule.WorkRestSchedule;

import jakarta.validation.Valid;

/**
 * HTTP API for managing crews and producing a day's heat plan.
 *
 * <p>Deliberately thin - it translates between HTTP and the domain and does nothing else, so
 * the scheduling logic stays testable without a web server.
 *
 * <p><b>No authentication in v0.1.</b> Crew ids are trusted as supplied. Crew rosters name
 * real people and their work patterns, so this must be fixed before exposure. Stated here in
 * the code rather than left as an unwritten assumption.
 */
@RestController
@RequestMapping("/api")
public class ShadeClockController {

    private final com.shadeclock.store.CrewRepository crews;
    private final ForecastSource forecastSource;
    private final ScheduleBuilder scheduleBuilder;
    private final HeatIndexCalculator calculator;
    private final RulesetRegistry registry;
    private final Clock clock;

    /**
     * @param crews           crew storage
     * @param forecastSource  where weather comes from
     * @param scheduleBuilder the scheduling engine
     * @param calculator      exposed for the heat index lookup endpoint
     * @param registry        exposed for the rules listing endpoint
     * @param clock           supplies today's date
     */
    public ShadeClockController(com.shadeclock.store.CrewRepository crews,
                                ForecastSource forecastSource,
                                ScheduleBuilder scheduleBuilder,
                                HeatIndexCalculator calculator,
                                RulesetRegistry registry,
                                Clock clock) {
        this.crews = crews;
        this.forecastSource = forecastSource;
        this.scheduleBuilder = scheduleBuilder;
        this.calculator = calculator;
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * Creates a crew.
     *
     * @param request the crew to create; validated before this method runs
     * @return {@code 201 Created} with the stored crew
     */
    @PostMapping("/crews")
    public ResponseEntity<Crew> createCrew(@Valid @RequestBody CrewRequest request) {
        List<Worker> workers = request.workers() == null ? List.of()
                : request.workers().stream()
                        .map(w -> new Worker(UUID.randomUUID().toString(), w.name(),
                                w.heatWorkStartedOn(), w.lastAbsenceEndedOn()))
                        .toList();

        Crew crew = new Crew(
                UUID.randomUUID().toString(),
                request.name(),
                request.siteLabel(),
                request.latitude(),
                request.longitude(),
                request.jurisdiction(),
                workers);

        return ResponseEntity.status(HttpStatus.CREATED).body(crews.save(crew));
    }

    /**
     * Lists every crew.
     *
     * @return the crews, possibly empty
     */
    @GetMapping("/crews")
    public List<Crew> listCrews() {
        return crews.findAll();
    }

    /**
     * Builds today's (or a given day's) heat plan for a crew.
     *
     * <p>The main endpoint. Returns the full schedule including its caveats - the verification
     * warning and the heat-index limitation travel with the plan rather than living only in
     * documentation nobody reads at 2pm on a roof.
     *
     * @param crewId the crew to plan for
     * @param date   the day to plan, defaulting to today
     * @return the schedule, or {@code 404} if the crew is unknown
     */
    @GetMapping("/crews/{crewId}/schedule")
    public ResponseEntity<ScheduleResponse> schedule(
            @PathVariable String crewId,
            @RequestParam(required = false) LocalDate date) {

        return crews.findById(crewId)
                .map(crew -> {
                    LocalDate target = date != null ? date : LocalDate.now(clock);
                    List<HourlyConditions> conditions = forecastSource.hourlyForecast(
                            crew.latitude(), crew.longitude(), target);
                    WorkRestSchedule built = scheduleBuilder.build(crew, target, conditions);
                    return ResponseEntity.ok(
                            new ScheduleResponse(built, forecastSource.describeSource()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Computes a heat index directly, for spot checks and for testing the maths.
     *
     * @param temperatureF air temperature in degrees Fahrenheit
     * @param humidity     relative humidity, 0-100
     * @return the heat index with its band and stated error
     */
    @GetMapping("/heat-index")
    public HeatIndexResponse heatIndex(@RequestParam double temperatureF,
                                       @RequestParam double humidity) {
        double hi = calculator.heatIndexF(temperatureF, humidity);
        return new HeatIndexResponse(
                temperatureF,
                humidity,
                (int) Math.round(hi),
                HeatIndexCalculator.STATED_ERROR_F,
                HeatRiskBand.forHeatIndex(hi).name(),
                HeatRiskBand.forHeatIndex(hi).description(),
                "Heat index assumes shade and light wind; it does not model direct sun or "
                        + "radiant heat.");
    }

    /**
     * Lists the jurisdictions ShadeClock has rules for.
     *
     * @return the known jurisdiction codes
     */
    @GetMapping("/jurisdictions")
    public List<String> jurisdictions() {
        return registry.knownJurisdictions();
    }

    /**
     * A schedule plus the provenance of the weather behind it.
     *
     * @param schedule       the plan
     * @param forecastSource where the weather came from
     */
    public record ScheduleResponse(WorkRestSchedule schedule, String forecastSource) {
    }

    /**
     * A heat index result with the uncertainty attached.
     *
     * @param temperatureF    input air temperature
     * @param humidity        input relative humidity
     * @param heatIndexF      computed heat index, rounded
     * @param statedErrorF    the regression's published error margin
     * @param band            NWS risk band name
     * @param bandDescription what that band means
     * @param limitation      what the number does not account for
     */
    public record HeatIndexResponse(
            double temperatureF,
            double humidity,
            int heatIndexF,
            double statedErrorF,
            String band,
            String bandDescription,
            String limitation) {
    }
}
