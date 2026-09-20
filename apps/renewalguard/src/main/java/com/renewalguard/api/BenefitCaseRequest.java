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
 * @param userId             the owner
 * @param program            which programme
 * @param stateCode          two-letter state code
 * @param category           eligibility group, which drives cadence from 2027
 * @param renewalDueOn       the renewal deadline
 * @param noticeReceivedOn   when the notice arrived, if it has
 * @param addressConfirmedOn when the address was last confirmed with the agency
 * @param responseWindowDays days to respond as stated on the user's notice, if known
 */
public record BenefitCaseRequest(
        @NotBlank(message = "userId is required")
        String userId,

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
