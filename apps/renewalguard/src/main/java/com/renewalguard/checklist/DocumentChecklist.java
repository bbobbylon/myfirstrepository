package com.renewalguard.checklist;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.renewalguard.domain.BenefitCase;
import com.renewalguard.domain.EnrollmentCategory;

/**
 * Produces the list of documents a renewal is likely to ask for.
 *
 * <h2>Why a checklist and not a document upload, in v0.1</h2>
 * The original proposal for RenewalGuard included an encrypted "document locker" holding
 * photographs of pay stubs, leases and IDs. <b>That is deliberately not built here</b>, and
 * the reasoning is worth stating plainly rather than quietly dropping the feature.
 *
 * <p>Storing identity documents for low-income households concentrates exactly the data an
 * identity thief wants, belonging to the people least able to absorb the consequences. Doing
 * it responsibly needs encryption at rest and in transit, key management, retention limits,
 * access logging and a real security review - none of which is a v0.1 afternoon's work, and
 * all of which is worse than useless if done badly.
 *
 * <p>So v0.1 ships the 80% of the value that carries none of that risk: <b>tell people
 * exactly what to find, and let them tick it off.</b> The checklist is what turns "renew your
 * coverage" from a vague dread into ten minutes of rummaging. Actually holding the files is a
 * v0.2 feature with a security review attached.
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
