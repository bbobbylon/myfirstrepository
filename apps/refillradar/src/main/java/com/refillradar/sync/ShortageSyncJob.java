package com.refillradar.sync;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.refillradar.alert.AlertService;
import com.refillradar.domain.ShortageRecord;
import com.refillradar.shortage.ShortageFetchException;
import com.refillradar.shortage.ShortageSource;

/**
 * The nightly job: fetch the FDA feed, cache it, and alert whoever is affected.
 *
 * <p>This is the piece that turns RefillRadar from a lookup tool into an early-warning
 * system. The product's whole premise is lead time - "your drug is short and your last pill
 * is in eleven days" - and lead time only exists if the application speaks first.
 *
 * <h2>Why it runs at 03:00</h2>
 * Alerts composed overnight are read in the morning, when a pharmacy or prescriber can
 * actually be called. An alert that arrives at 9pm is one the reader can do nothing about
 * for twelve hours, which converts useful information into a night of worry.
 *
 * <h2>Failure behaviour, which is the interesting part</h2>
 * A failed fetch does <b>not</b> clear {@link ShortageCache}, and does not cause an alerting
 * pass over empty data. Both would be ways of telling users "nothing is short" because our
 * network was down - the exact false reassurance this application exists to prevent.
 *
 * <p>Instead the failure is logged, the previous feed stays in place with its age visible,
 * and the next run tries again.
 */
@Component
public class ShortageSyncJob {

    private static final Logger log = LoggerFactory.getLogger(ShortageSyncJob.class);

    private final ShortageSource source;
    private final ShortageCache cache;
    private final AlertService alerts;
    private final Clock clock;

    /**
     * @param source where shortage data comes from
     * @param cache  last-known-good storage
     * @param alerts the alerting pipeline
     * @param clock  injected time source
     */
    public ShortageSyncJob(ShortageSource source, ShortageCache cache,
                           AlertService alerts, Clock clock) {
        this.source = source;
        this.cache = cache;
        this.alerts = alerts;
        this.clock = clock;
    }

    /**
     * Runs the sync on a schedule.
     *
     * <p>Cron is configurable so a deployment can shift it to suit its users' timezone, and
     * so tests never trip it accidentally.
     *
     * @return the outcome, also returned so the manual trigger can report it
     */
    @Scheduled(cron = "${refillradar.sync.cron:0 0 3 * * *}")
    public SyncOutcome runScheduledSync() {
        return runSync();
    }

    /**
     * Runs the sync now.
     *
     * <p>Exposed for the admin endpoint. Always give a scheduled job a manual trigger -
     * otherwise every test cycle costs you a day, and you end up testing in production.
     *
     * @return what happened
     */
    public SyncOutcome runSync() {
        List<ShortageRecord> records;
        try {
            records = source.fetchCurrentShortages();
        } catch (ShortageFetchException e) {
            // Deliberately does NOT clear the cache or run an alerting pass. Telling users
            // "nothing is short" because our network failed is the one thing this
            // application must never do.
            log.error("Shortage sync failed - keeping previous data, sending no alerts", e);
            return new SyncOutcome(false, 0, 0, 0, 0, clock.instant(),
                    "Fetch failed: " + e.getMessage()
                            + ". Previous shortage data was kept and no alerts were sent. "
                            + "This is NOT an all-clear.");
        }

        cache.store(records, source.describeSource());
        AlertService.AlertRun run = alerts.run(records, source.describeSource());

        String note = "Fetched " + records.size() + " records from " + source.describeSource()
                + ". Alerts sent: " + run.alertsSent()
                + ", suppressed as already-sent: " + run.suppressed()
                + ", channel: " + run.channel() + ".";
        if (!run.unreachable().isEmpty()) {
            note += " " + run.unreachable().size()
                    + " user(s) needed an alert but have no contact route.";
        }

        log.info(note);
        return new SyncOutcome(true, records.size(), run.usersEvaluated(),
                run.alertsSent(), run.suppressed(), clock.instant(), note);
    }
}
