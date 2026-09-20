package com.shadeclock;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Application entry point for ShadeClock.
 *
 * <p>ShadeClock turns a weather forecast into a work/rest timetable for an outdoor crew,
 * applying the heat rules of the state they are standing in and tracking which workers are
 * not yet heat-adapted.
 *
 * <p>Run with {@code ./mvnw spring-boot:run}, or as a JAR with
 * {@code java -jar target/shadeclock-0.1.0-SNAPSHOT.jar}.
 *
 * @see com.shadeclock.schedule.ScheduleBuilder the engine
 * @see com.shadeclock.rules.HeatRuleset the jurisdiction rules
 * @see com.shadeclock.heat.HeatIndexCalculator the NWS measurement underneath it all
 */
@SpringBootApplication
public class ShadeClockApplication {

    /**
     * Boots the Spring context and starts the embedded web server.
     *
     * @param args standard JVM arguments, forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ShadeClockApplication.class, args);
    }

    /**
     * The application's time source.
     *
     * <p>Same reasoning as RefillRadar: injecting a {@link Clock} rather than calling
     * {@code LocalDate.now()} makes acclimatisation maths testable. "A worker who started
     * two days ago is UNACCLIMATIZED" has to stay true next month, and it only does if the
     * test controls what "today" means.
     *
     * @return the system UTC clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
