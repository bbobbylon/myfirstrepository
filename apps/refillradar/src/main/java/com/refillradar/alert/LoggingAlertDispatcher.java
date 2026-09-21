package com.refillradar.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Writes alerts to the log instead of sending them. The default.
 *
 * <p>Active unless {@code refillradar.alert-channel} says otherwise, so a deployment that
 * has not configured mail produces visible, inspectable output rather than quietly doing
 * nothing.
 *
 * <p>Crucially it reports {@code delivered = false}. Claiming delivery when a message only
 * reached a log file would be the same class of lie as reporting "no shortages" during an
 * outage - and the whole application is built around not telling that kind of lie.
 */
@Component
@ConditionalOnProperty(name = "refillradar.alert-channel", havingValue = "log",
        matchIfMissing = true)
public class LoggingAlertDispatcher implements AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertDispatcher.class);

    /** {@inheritDoc} */
    @Override
    public DispatchResult dispatch(String userId, String subject, String body) {
        log.info("ALERT for user {} | {}\n{}", userId, subject, body);
        return new DispatchResult(false,
                "Written to the application log only - no message was actually sent. "
                        + "Configure refillradar.alert-channel=email to deliver alerts.");
    }

    /** {@inheritDoc} */
    @Override
    public String describeChannel() {
        return "Application log (NOT DELIVERED - development default)";
    }
}
