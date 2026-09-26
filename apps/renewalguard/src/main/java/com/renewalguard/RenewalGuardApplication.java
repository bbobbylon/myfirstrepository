package com.renewalguard;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Application entry point for RenewalGuard.
 *
 * <p>RenewalGuard defends benefits renewal deadlines. Roughly <b>70% of Medicaid
 * terminations are procedural</b> - a missed deadline, an undelivered notice, paperwork never
 * returned - rather than a finding that someone stopped qualifying. The dominant failure mode
 * of the safety net is not fraud or overspending. It is stationery.
 *
 * <p>Run with {@code ./mvnw spring-boot:run}, or as a JAR with
 * {@code java -jar target/renewalguard-0.1.0-SNAPSHOT.jar}. Since v0.2 it needs PostgreSQL -
 * see {@code README.md} for the one command that starts one.
 *
 * <p><b>Scanning note.</b> The shared auth code lives in {@code com.commonauth}, outside this
 * class's package, so component scanning is widened to reach it. The matching JPA entity and
 * repository scans live in {@code JpaScanConfig} instead, because they must not apply under
 * the {@code memory} profile - see that class for what broke.
 *
 * @see com.renewalguard.rules.RenewalCadence the 2027 six-month renewal change
 * @see com.renewalguard.remind.ReminderLadder the escalation schedule
 */
@SpringBootApplication(scanBasePackages = {"com.renewalguard", "com.commonauth"})
public class RenewalGuardApplication {

    /**
     * Boots the Spring context and starts the embedded web server.
     *
     * @param args standard JVM arguments, forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(RenewalGuardApplication.class, args);
    }

    /**
     * The application's time source.
     *
     * <p>Injected rather than ambient, so "a renewal due in 5 days is CRITICAL" stays a
     * stable assertion instead of drifting with the calendar. Same pattern as RefillRadar
     * and ShadeClock.
     *
     * @return the system UTC clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
