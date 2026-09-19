package com.refillradar.refill;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.refillradar.domain.Medication;
import com.refillradar.domain.SupplyRisk;

/**
 * Works out how much supply a patient has left and how urgent that makes a shortage.
 *
 * <h2>Why this takes a {@link Clock} instead of calling {@code LocalDate.now()}</h2>
 * Code that asks the system for the current time is nearly impossible to test properly.
 * You cannot write "assert that a medication running out in 3 days is CRITICAL" if the
 * answer changes every midnight - the test passes today and fails next Tuesday for no
 * reason anyone can see.
 *
 * <p>Injecting a {@link Clock} makes time an <em>input</em> rather than an ambient fact.
 * Production supplies the real clock; tests supply a frozen one. This is the same idea as
 * handing a chef the ingredients rather than letting them wander off to the shop mid-recipe:
 * you can only reproduce the dish if you control what went into it.
 *
 * <p>This is a small habit with a large payoff, and it is worth adopting permanently - date
 * bugs are among the most common and most embarrassing in production software.
 */
@Component
public class RefillProjector {

    private final Clock clock;

    /**
     * Creates a projector bound to a clock.
     *
     * <p>Spring injects the application's {@code Clock} bean automatically. See
     * {@code RefillRadarApplication} for where the production clock is defined.
     *
     * @param clock the time source to evaluate against; must not be {@code null}
     */
    public RefillProjector(Clock clock) {
        this.clock = clock;
    }

    /**
     * The date this projector currently considers "today".
     *
     * @return today according to the injected clock
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Days of supply remaining before the projected run-out date.
     *
     * <p>A negative result means the projected supply is already exhausted - normally
     * because the user refilled without telling us, so their stored data is stale.
     *
     * @param medication the medication to project; must not be {@code null}
     * @return whole days from today to the run-out date; negative when already past
     */
    public long daysRemaining(Medication medication) {
        return ChronoUnit.DAYS.between(today(), medication.projectedRunOutDate());
    }

    /**
     * Classifies a medication's remaining supply into an urgency level.
     *
     * @param medication the medication to assess; must not be {@code null}
     * @return the urgency level for this patient, never {@code null}
     */
    public SupplyRisk assessRisk(Medication medication) {
        return SupplyRisk.fromDaysRemaining(daysRemaining(medication));
    }
}
