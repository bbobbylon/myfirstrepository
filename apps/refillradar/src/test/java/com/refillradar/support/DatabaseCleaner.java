package com.refillradar.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empties every application table between integration tests.
 *
 * <p><b>Why truncation rather than {@code @Transactional} on the test class.</b> Annotating
 * the test rolls back automatically and is less code - but it also means the writes never
 * commit. A bad {@code @Column} mapping, a constraint the entity violates, a value the
 * driver cannot bind: all of those surface at flush or commit, so a test that never commits
 * cannot see them. Since the entire point of v0.3 is that data really lands in PostgreSQL,
 * these tests commit for real and clean up afterwards.
 *
 * <p>This also makes the leak visible rather than magic. An earlier run of the integration
 * test left its row behind and passed only because the database happened to be empty; the
 * second run failed on a count assertion. Tests that share a database need their isolation
 * written down.
 *
 * <p><b>Deliberately not {@code @Component}.</b> Test sources are on the classpath that
 * {@code @SpringBootTest} component-scans, so a stereotype annotation here would make this a
 * bean in <em>every</em> Spring test in the project - including {@code MemoryProfileTest},
 * which has no {@code DataSource} and therefore no {@code JdbcTemplate} to inject. It is
 * pulled in explicitly with {@code @Import} by the tests that actually want it.
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
     * <p>Deliberately does not touch {@code flyway_schema_history} - wiping that would make
     * the next startup try to re-run migrations against a schema that already exists.
     */
    @Transactional
    public void clean() {
        jdbc.execute("TRUNCATE TABLE medications, contacts, alert_records");
    }
}
