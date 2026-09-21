package com.renewalguard.remind;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Decides which days before a deadline get a reminder.
 *
 * <p><b>Escalation, not repetition.</b> Daily reminders for two months train people to
 * ignore them, and an ignored reminder carries false reassurance. One reminder sixty days
 * out is forgotten by the time it matters. So the ladder is sparse far out, dense close in:
 * <ul>
 *   <li><b>60 and 45 days</b> - plan: check your address, find your documents</li>
 *   <li><b>30, 21, 14 days</b> - act: complete the renewal</li>
 *   <li><b>10 days and every day after</b> - urgent: this is the window where states can
 *       close a case, and where 14 of them reportedly allow only 10 days to respond</li>
 * </ul>
 *
 * <p>Roughly <b>70% of Medicaid terminations are procedural</b> rather than a finding that
 * someone no longer qualifies. Nobody wants those, including the state, which pays to
 * process the re-application weeks later.
 */
@Component
public class ReminderLadder {

    /** Days before the deadline that get a scheduled reminder, descending. */
    private static final List<Integer> MILESTONES = List.of(60, 45, 30, 21, 14, 10);

    /** Inside this many days, remind every day. */
    public static final int DAILY_FROM_DAYS = 10;

    /**
     * Whether a reminder should be sent today.
     *
     * @param daysUntilDue days from today to the deadline; negative if past
     * @return {@code true} if today is a reminder day
     */
    public boolean shouldRemindToday(long daysUntilDue) {
        // Past due still reminds: reinstatement is often possible if acted on quickly, so
        // going quiet at the moment of failure is the worst possible behaviour.
        if (daysUntilDue <= DAILY_FROM_DAYS) {
            return true;
        }
        return MILESTONES.contains((int) daysUntilDue);
    }

    /**
     * The full reminder schedule for a deadline, as days-before values.
     *
     * <p>Shown to users up front so the cadence is predictable rather than surprising - and
     * so anyone who finds it too much can see exactly what they are opting out of.
     *
     * @return the milestone days, descending, never {@code null}
     */
    public List<Integer> milestones() {
        return MILESTONES;
    }

    /**
     * Whether this point in the ladder is the urgent, daily phase.
     *
     * @param daysUntilDue days from today to the deadline
     * @return {@code true} inside the final window
     */
    public boolean isDailyPhase(long daysUntilDue) {
        return daysUntilDue <= DAILY_FROM_DAYS;
    }
}
