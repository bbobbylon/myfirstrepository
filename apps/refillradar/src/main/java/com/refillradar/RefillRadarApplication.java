package com.refillradar;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point for RefillRadar.
 *
 * <p>RefillRadar watches the US FDA drug shortage database against the medications a user
 * has registered and warns them <em>before</em> a refill fails, rather than at the pharmacy
 * counter on the day they run out.
 *
 * <p>Run it with {@code ./mvnw spring-boot:run}, or as a JAR with
 * {@code java -jar target/refillradar-0.1.0-SNAPSHOT.jar}.
 *
 * <p><b>v0.2 closes the loop.</b> v0.1 could answer "is my medication short?" when asked,
 * but could not tell anyone - which meant it did not actually deliver lead time, the one
 * thing it promises. {@link com.refillradar.sync.ShortageSyncJob} now runs nightly and
 * speaks first.
 *
 * @see com.refillradar.matching.ShortageMatcher the core engine
 * @see com.refillradar.shortage.ShortageSource where shortage data comes from
 * @see com.refillradar.sync.ShortageSyncJob the nightly sync that closes the loop
 */
@SpringBootApplication
@EnableScheduling
public class RefillRadarApplication {

    /**
     * Boots the Spring context and starts the embedded web server.
     *
     * @param args standard JVM arguments, forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(RefillRadarApplication.class, args);
    }

    /**
     * The application's time source.
     *
     * <p>Exposing the clock as a bean - rather than letting each class call
     * {@code LocalDate.now()} - is what makes every date-dependent behaviour in this
     * codebase testable. A test replaces this single bean with {@code Clock.fixed(...)} and
     * the entire application believes it is a specific day, forever, deterministically.
     *
     * <p>Without it you cannot write "a medication running out in three days is CRITICAL" as
     * a test that still passes next month. With it, that test is trivial. See
     * {@link com.refillradar.refill.RefillProjector} for the fuller explanation.
     *
     * @return the system UTC clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
