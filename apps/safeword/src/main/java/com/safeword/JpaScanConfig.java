package com.safeword;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Points JPA at this application's tables <em>and</em> at the shared auth module's.
 *
 * <p><b>Why these annotations are here rather than on {@code SafeWordApplication}.</b>
 * They were there first, and it broke the {@code memory} profile: that profile excludes
 * the JPA auto-configuration entirely, but {@code @EnableJpaRepositories} switches the
 * repository infrastructure on regardless, so the context died trying to build an
 * {@code entityManagerFactory} with no {@code DataSource} behind it. Moving them to a
 * configuration annotated {@code @Profile("!memory")} keeps the two facts together: JPA
 * scanning and a real database are the same decision.
 *
 * <p><b>And why both packages are listed.</b> {@code @EntityScan} and
 * {@code @EnableJpaRepositories} <em>replace</em> Spring Boot's default scan - "the package
 * of the application class, and below" - rather than adding to it. Name only the shared
 * package and this application's own entities quietly stop being mapped.
 */
@Configuration
@Profile("!memory")
@EntityScan(basePackages = {"com.safeword.store.jpa", "com.commonauth.store.jpa"})
@EnableJpaRepositories(basePackages = {"com.safeword.store.jpa", "com.commonauth.store.jpa"})
public class JpaScanConfig {
}
