package com.refillradar.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.refillradar.store.ContactRepository;

/**
 * Sends alerts by email.
 *
 * <p>Active only when {@code refillradar.alert-channel=email}. The default is the logging
 * dispatcher, so a deployment that has not configured SMTP starts cleanly and produces
 * visible output rather than silently failing to notify anyone.
 *
 * <p><b>⚠️ Never exercised against a real SMTP server.</b> The build environment has no mail
 * relay and blocks outbound SMTP, so this class is written against Spring's
 * {@link JavaMailSender} contract and tested with a stub. Treat the first live run as part
 * of the work: confirm the from-address is accepted, check deliverability, and watch what
 * lands in spam folders - a shortage warning in a junk folder is a warning nobody receives.
 *
 * <p>SMS would reach this older-skewing audience more reliably, but carries per-message cost
 * and US A2P 10DLC registration - procurement, not coding. Email first is the honest
 * sequencing; SMS is v0.3.
 */
@Component
@ConditionalOnProperty(name = "refillradar.alert-channel", havingValue = "email")
public class EmailAlertDispatcher implements AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailAlertDispatcher.class);

    private final JavaMailSender mailSender;
    private final ContactRepository contacts;
    private final String fromAddress;

    /**
     * @param mailSender  Spring's configured mail sender
     * @param contacts    resolves a user id to an address
     * @param fromAddress the envelope sender
     */
    public EmailAlertDispatcher(JavaMailSender mailSender,
                                ContactRepository contacts,
                                @Value("${refillradar.mail.from:alerts@refillradar.invalid}")
                                String fromAddress) {
        this.mailSender = mailSender;
        this.contacts = contacts;
        this.fromAddress = fromAddress;
    }

    /** {@inheritDoc} */
    @Override
    public DispatchResult dispatch(String userId, String subject, String body) {
        String to = contacts.findEmail(userId).orElse(null);
        if (to == null) {
            // Reported, never silently dropped. A user we cannot reach is a failure of the
            // product's core promise and somebody should be able to see it happened.
            return new DispatchResult(false,
                    "No email address on file for user " + userId + " - nothing was sent.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            return new DispatchResult(true, "Sent to " + to);
        } catch (MailException e) {
            // Returned rather than thrown: one user's bad address must not abort the whole
            // nightly run and deprive everyone else of their alert.
            log.error("Failed to email alert to user {}", userId, e);
            return new DispatchResult(false, "Send failed: " + e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public String describeChannel() {
        return "Email via SMTP (from " + fromAddress + ")";
    }
}
