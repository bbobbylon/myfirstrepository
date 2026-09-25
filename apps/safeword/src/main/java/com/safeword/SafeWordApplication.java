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
 * <p>A different layer from account-monitoring products (Carefull, EverSafe), which fire
 * <b>after</b> a transaction - too late for the irreversible instruments these scams choose.
 *
 * <p>Run with {@code ./mvnw spring-boot:run}, or as a JAR.
 *
 * <p><b>Scanning note.</b> The shared auth code lives in {@code com.commonauth}, outside
 * this class's package, so component scanning is widened to reach it. The matching JPA
 * entity and repository scans live in {@code JpaScanConfig} instead, because they must not
 * apply under the {@code memory} profile - see that class for what broke.
 *
 * @see com.safeword.passphrase.PassphraseProtocol why a shared secret beats deepfake detection
 * @see com.safeword.pause.PauseChecklist why friction is the countermeasure
 */
@SpringBootApplication(scanBasePackages = {"com.safeword", "com.commonauth"})
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
