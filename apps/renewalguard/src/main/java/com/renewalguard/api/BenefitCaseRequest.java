package com.renewalguard.api;

import java.time.LocalDate;

import com.renewalguard.domain.BenefitProgram;
import com.renewalguard.domain.EnrollmentCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Incoming payload for tracking a benefit case.
 *
 * <p>Note what is <b>not</b> here: no SSN, no income, no household size, no immigration
 * status. RenewalGuard tracks deadlines and paperwork, so it does not ask for the data an
 * eligibility determination would need. Refusing to collect it is the strongest privacy
 * control available.
 *
 * <p><b>And no {@code userId} since v0.2.</b> It used to be the first field, and the server
 * trusted it, so a caller could file a case under anybody's account. The owner now comes from
 * the session. Deleting the field rather than validating it is the stronger fix: there is no
 * longer anywhere for a future handler to read an attacker-supplied owner from, so the bug
 * cannot be reintroduced by someone who simply forgets to check.
 *
 * @param program            which programme
 * @param stateCode          two-letter state code
 * @param category           eligibility group, which drives cadence from 2027
 * @param renewalDueOn       the renewal deadline
 * @param noticeReceivedOn   when the notice arrived, if it has
 * @param addressConfirmedOn when the address was last confirmed with the agency
 * @param responseWindowDays days to respond as stated on the user's notice, if known
 */
public record BenefitCaseRequest(

        BenefitProgram program,

        @NotBlank(message = "stateCode is required")
        @Size(min = 2, max = 2, message = "stateCode must be two letters, e.g. CA")
        String stateCode,

        EnrollmentCategory category,

        @NotNull(message = "renewalDueOn is required (format: YYYY-MM-DD)")
        LocalDate renewalDueOn,

        LocalDate noticeReceivedOn,

        LocalDate addressConfirmedOn,

        @Positive(message = "responseWindowDays must be positive if supplied")
        Integer responseWindowDays) {
}
