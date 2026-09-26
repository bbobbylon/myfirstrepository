package com.shadeclock.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empties every application table between integration tests.
 *
 * <p><b>Why truncation rather than {@code @Transactional} on the test class.</b> Annotating the
 * test rolls back automatically and is less code - but it also means the writes never commit. A
 * bad {@code @Column} mapping, a CHECK constraint the entity violates, an {@code orphanRemoval}
 * that does not fire: all of those surface at flush or commit, so a test that never commits
 * cannot see them. Since the point of v0.2 is that rosters really land in PostgreSQL, these
 * tests commit for real and clean up afterwards.
 *
 * <p><b>Deliberately not {@code @Component}.</b> Test sources are on the classpath that
 * {@code @SpringBootTest} component-scans, so a stereotype annotation here would make this a bean
 * in <em>every</em> Spring test in the project - including {@code MemoryProfileTest}, which has
 * no {@code DataSource} and therefore no {@code JdbcTemplate} to inject. It is pulled in
 * explicitly with {@code @Import} by the tests that actually want it.
 */
public class DatabaseCleaner {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc direct SQL access, bypassing JPA so no entity state is cached
     */
    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Removes every row from every application table.
     *
     * <p>Deliberately does not touch {@code flyway_schema_history} - wiping that would make the
     * next startup try to re-run migrations against a schema that already exists.
     */
    @Transactional
    public void clean() {
        // crew_workers and crews go in the same statement as users: PostgreSQL refuses to
        // truncate a table another one references unless they are named together.
        jdbc.execute("TRUNCATE TABLE crew_workers, crews, users");
        // Failed logins outlive the test that caused them, and MockMvc gives every request the
        // same remote address - so without this the per-address limit would be reached partway
        // through a class and fail whichever test happened to run next.
        jdbc.execute("TRUNCATE TABLE login_attempts");
        // Sessions reference nothing, but a leftover login from a previous test would make an
        // "unauthenticated request is refused" assertion pass or fail by accident.
        jdbc.execute("TRUNCATE TABLE SPRING_SESSION CASCADE");
    }
}
