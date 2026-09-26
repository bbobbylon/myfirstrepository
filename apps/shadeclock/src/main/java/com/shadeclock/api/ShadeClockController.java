package com.shadeclock.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.commonauth.web.AccountPrincipal;
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
 * <p><b>v0.2 closed what v0.1 documented right here.</b> The old comment said "crew ids are
 * trusted as supplied" and warned that rosters name real people. It understated the problem: the
 * IDOR on the schedule route was the lesser half, because the listing needed no id at all.
 *
 * <pre>
 * v0.1   GET    /api/crews                     &#8592; crews.findAll(), open to anyone
 * v0.2   GET    /api/me/crews                  &#8592; the session decides whose
 *
 * v0.1   GET    /api/crews/&#123;crewId&#125;/schedule    &#8592; no check whatsoever
 * v0.2   GET    /api/me/crews/&#123;crewId&#125;/schedule &#8592; owner is in the query
 *
 * v0.1   POST   /api/crews                     &#8592; crew belonged to nobody
 * v0.2   POST   /api/crews                     &#8592; owner comes from the session
 * </pre>
 *
 * <p>One unauthenticated request used to return every crew in the system: every worker's name,
 * the dates from which a week-or-more absence can be inferred, and the GPS coordinates of every
 * work site. {@code findAll()} no longer exists on {@code CrewRepository} - it had no other
 * caller and this app has no batch job, so it is gone rather than guarded.
 *
 * <p><b>404, never 403, for somebody else's crew.</b> A 403 would confirm the id is real, and
 * "real, just not yours" is what an id enumerator wants to learn. {@code notFound()} below is a
 * security decision rather than laziness about status codes.
 *
 * <p>Two routes stay open on purpose - the heat-index calculator and the jurisdiction list. See
 * {@code SecurityConfig} for why locking them would be its own kind of bug.
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
     * Creates a crew, owned by whoever is logged in.
     *
     * @param request   the crew to create; validated before this method runs
     * @param principal the authenticated supervisor, resolved from the session cookie
     * @return {@code 201 Created} with the stored crew
     */
    @PostMapping("/crews")
    public ResponseEntity<Crew> createCrew(@Valid @RequestBody CrewRequest request,
                                           @AuthenticationPrincipal AccountPrincipal principal) {
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

        // The owner comes from the session. CrewRequest has no field for it, so there is
        // nowhere for a caller to put someone else's account id even if they tried.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(crews.save(principal.accountId(), crew));
    }

    /**
     * Lists the logged-in supervisor's own crews.
     *
     * <p><b>This method is the v0.1 vulnerability.</b> It was {@code GET /api/crews} calling
     * {@code crews.findAll()}, reachable with no credentials, so one request returned every
     * roster in the system. The repository no longer has a {@code findAll()} to call.
     *
     * @param principal the authenticated supervisor
     * @return their crews, possibly empty
     */
    @GetMapping("/me/crews")
    public List<Crew> listMyCrews(@AuthenticationPrincipal AccountPrincipal principal) {
        return crews.findByOwner(principal.accountId());
    }

    /**
     * Builds today's (or a given day's) heat plan for a crew.
     *
     * <p>The main endpoint. Returns the full schedule including its caveats - the verification
     * warning and the heat-index limitation travel with the plan rather than living only in
     * documentation nobody reads at 2pm on a roof.
     *
     * @param crewId    the crew to plan for
     * @param date      the day to plan, defaulting to today
     * @param principal the authenticated supervisor, who must own the crew
     * @return the schedule, or {@code 404} if the crew is unknown <em>or</em> not theirs - the
     *         two are deliberately indistinguishable
     */
    @GetMapping("/me/crews/{crewId}/schedule")
    public ResponseEntity<ScheduleResponse> schedule(
            @PathVariable String crewId,
            @RequestParam(required = false) LocalDate date,
            @AuthenticationPrincipal AccountPrincipal principal) {

        return crews.findByIdAndOwner(crewId, principal.accountId())
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
     * Stops tracking one of your own crews.
     *
     * <p>New in v0.2, and owner-scoped from birth. This app holds named workers, so a supervisor
     * needs a way to remove a roster once a job ends - an app that can only accumulate people's
     * names is a retention problem, not a feature.
     *
     * @param crewId    the crew to remove
     * @param principal the authenticated supervisor, who must own it
     * @return {@code 204 No Content} on success, or {@code 404} if unknown or not theirs
     */
    @DeleteMapping("/me/crews/{crewId}")
    public ResponseEntity<Void> deleteCrew(@PathVariable String crewId,
                                           @AuthenticationPrincipal AccountPrincipal principal) {
        return crews.deleteByIdAndOwner(crewId, principal.accountId())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
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
