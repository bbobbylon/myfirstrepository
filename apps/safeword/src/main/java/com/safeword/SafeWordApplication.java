package com.safeword;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Application entry point for SafeWord.
 *
 * <p>SafeWord intervenes at the moment of the call - the thirty seconds before money moves -
 * with a pre-agreed family passphrase, a forced pause, and one-tap escalation to relatives.
 *
 * <p>It is deliberately a <em>different layer</em> from account-monitoring products such as
 * Carefull and EverSafe, which are good at what they do but fire <b>after</b> a transaction.
 * For a wire transfer or gift cards - instruments these scams choose precisely because they
 * are irreversible - an alert after the fact tells a family the money is gone. Running both
 * together is entirely reasonable.
 *
 * <p>Run with {@code ./mvnw spring-boot:run}, or as a JAR.
 *
 * @see com.safeword.passphrase.PassphraseProtocol why a shared secret beats deepfake detection
 * @see com.safeword.pause.PauseChecklist why friction is the countermeasure
 */
@SpringBootApplication
public class SafeWordApplication {

    /**
     * Boots the Spring context and starts the embedded web server.
     *
     * @param args standard JVM arguments, forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(SafeWordApplication.class, args);
    }

    /**
     * The application's time source, injected for testability as in the other apps.
     *
     * @return the system UTC clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
