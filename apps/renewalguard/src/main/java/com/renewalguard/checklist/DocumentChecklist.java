package com.renewalguard.checklist;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.EnrollmentCategory;

/**
 * Produces the list of documents a renewal is likely to ask for.
 *
 * <p><b>A checklist, not a document locker.</b> The proposal called for encrypted storage of
 * pay stubs, leases and IDs - deliberately not built. That concentrates exactly what an
 * identity thief wants, belonging to people least able to absorb it, and doing it responsibly
 * needs key management, retention limits and a security review.
 *
 * <p>v0.1 ships the value that carries none of that risk: tell people what to find and where
 * it usually lives. Holding the files is v0.2, with a security review attached.
 *
 * <p><b>What this class never does:</b> it never says whether the documents will qualify
 * someone. Eligibility is the agency's determination. A tool that implied otherwise could
 * talk someone out of a renewal they would have won.
 */
@Service
public class DocumentChecklist {

    /**
     * Builds the likely document list for a case.
     *
     * <p>Deliberately framed as "commonly asked for", not "required". The authoritative list
     * is whatever the person's own notice says, and the checklist says so.
     *
     * @param benefitCase the case to build a checklist for
     * @return the documents, never {@code null}
     */
    public List<RequiredDocument> forCase(BenefitCase benefitCase) {
        List<RequiredDocument> documents = new ArrayList<>();

        documents.add(new RequiredDocument(
                "Proof of income for everyone in your household",
                "The agency checks income against the programme's limit.",
                "Recent pay stubs, a benefits award letter, or a self-employment ledger. "
                        + "Most agencies want the last 30 days.",
                false));

        documents.add(new RequiredDocument(
                "Proof of where you live",
                "Confirms your address and which state's programme you are in.",
                "A lease, a utility bill, or a letter addressed to you at your current home.",
                true));

        documents.add(new RequiredDocument(
                "Photo ID for the head of household",
                "Confirms identity.",
                "Driver's licence, state ID card, or passport.",
                false));

        if (benefitCase.category() == EnrollmentCategory.CHILD
                || benefitCase.category() == EnrollmentCategory.UNKNOWN) {
            documents.add(new RequiredDocument(
                    "Proof of age or school enrolment for children on the case",
                    "Confirms children still qualify under the children's category.",
                    "Birth certificate, or a school enrolment letter for the current year.",
                    true));
        }

        if (benefitCase.category() == EnrollmentCategory.AGED_BLIND_DISABLED) {
            documents.add(new RequiredDocument(
                    "Any current disability or Medicare documentation",
                    "Supports the eligibility pathway your case is under.",
                    "Award letters or determination notices you have already received.",
                    true));
        }

        documents.add(new RequiredDocument(
                "The renewal notice itself",
                "It states your real deadline and exactly what your agency wants.",
                "The envelope from your state Medicaid agency. If you cannot find it, call "
                        + "them - they can resend it and confirm your deadline.",
                true));

        return documents;
    }

    /**
     * The items people most often forget, for a short "don't miss these" prompt.
     *
     * @param benefitCase the case
     * @return the commonly-missed subset, never {@code null}
     */
    public List<RequiredDocument> commonlyMissed(BenefitCase benefitCase) {
        return forCase(benefitCase).stream().filter(RequiredDocument::oftenMissed).toList();
    }
}
