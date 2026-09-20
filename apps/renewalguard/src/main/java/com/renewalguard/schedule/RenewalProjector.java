package com.renewalguard.schedule;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.RenewalUrgency;

/**
 * Works out how long is left before a renewal deadline and how urgent that makes it.
 *
 * <p>Takes an injected {@link Clock} for the same reason as RefillRadar and ShadeClock: a
 * test asserting "a renewal due in 5 days is CRITICAL" must stay true next month, and it
 * only does if the test controls what "today" means.
 */
@Component
public class RenewalProjector {

    /** How long an address confirmation is treated as still trustworthy. */
    public static final int ADDRESS_STALE_AFTER_DAYS = 180;

    private final Clock clock;

    /**
     * @param clock the time source to evaluate against
     */
    public RenewalProjector(Clock clock) {
        this.clock = clock;
    }

    /**
     * Today, according to the injected clock.
     *
     * @return today's date
     */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Days from today until the renewal deadline.
     *
     * @param benefitCase the case to project
     * @return whole days; negative if the deadline has passed
     */
    public long daysUntilDue(BenefitCase benefitCase) {
        return ChronoUnit.DAYS.between(today(), benefitCase.renewalDueOn());
    }

    /**
     * The urgency of this case right now.
     *
     * @param benefitCase the case to assess
     * @return the urgency, never {@code null}
     */
    public RenewalUrgency urgency(BenefitCase benefitCase) {
        return RenewalUrgency.fromDaysUntilDue(daysUntilDue(benefitCase));
    }

    /**
     * The date by which documents should realistically be ready.
     *
     * <p>Works backwards from the deadline by the response window, because the deadline is
     * not really the deadline: if an agency asks for a pay stub with 10 days to respond, the
     * person needs that document <em>before</em> the request arrives, not after.
     *
     * @param benefitCase        the case
     * @param responseWindowDays the window to plan against
     * @return the date documents should be in hand by
     */
    public LocalDate documentsReadyBy(BenefitCase benefitCase, int responseWindowDays) {
        return benefitCase.renewalDueOn().minusDays(responseWindowDays);
    }

    /**
     * Whether this case's address should be re-confirmed.
     *
     * @param benefitCase the case to test
     * @return {@code true} if the address is unconfirmed or stale
     */
    public boolean needsAddressCheck(BenefitCase benefitCase) {
        return benefitCase.needsAddressCheck(today(), ADDRESS_STALE_AFTER_DAYS);
    }
}
