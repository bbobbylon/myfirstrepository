# common-auth

**Accounts, sessions and login rate limiting, shared by the apps in this repository.**

Not an application — a plain library jar. It has no `main`, no `application.yml` and no
migrations; it contributes beans to whichever app depends on it.

## Why it exists

RefillRadar v0.4–v0.5 built accounts, sessions and a login throttle. SafeWord v0.2 needed
the same three things and got them by copy and paste. A third copy is where that stops being
acceptable: **this is security code, and N copies of a login throttle is exactly how one app
silently keeps a bug after the others are fixed.**

The usual advice is to wait for a third occurrence before abstracting, and that is good
advice for ordinary code — an abstraction drawn from one example is a guess. For a control
whose failure mode is a breach, the second occurrence is the signal.

**The third app is the receipt.** RenewalGuard v0.2 adopted the whole stack as a dependency
line, a fifteen-line `SecurityConfig` holding only its route rules, and a `JpaScanConfig` — no
copied login throttle, no copied session config, no third chance to fix a bug in two places out
of three. Consumers: **RefillRadar**, **SafeWord**, **RenewalGuard**.

## What is in here

| | |
|---|---|
| `domain/` | `Account`, `AccountRole` |
| `store/` | `AccountRepository`, `LoginAttemptStore`, and in-memory implementations |
| `store/jpa/` | the PostgreSQL implementations and their entities |
| `web/` | `AuthController` (`/api/auth/*`), `AccountPrincipal`, `AccountDetailsService`, `LoginThrottle`, `InsecureCookieWarning` |
| `config/` | `AuthBeans` (password encoder, authentication manager) and `AuthHardening` |

## What is deliberately *not* in here

- **Flyway migrations.** Each app owns its own version line, and a shared migration numbered
  `V1` would collide with whatever that app already calls `V1`. The `users`,
  `login_attempts` and `SPRING_SESSION` DDL is therefore repeated per app; the Java is not.
  If you add a column here, every consuming app needs its own migration for it.
- **Authorization rules.** Which routes are public is a *product* decision that differs per
  app — SafeWord leaves its pause screen and call checker open to anyone on purpose, because
  someone being pressured by a stranger on the phone must not meet a login wall, while
  RenewalGuard makes everything but registration and login private because it has no such
  moment. Only the hardening is shared. A route should never become public in a file nobody
  read.

## Using it

```java
@SpringBootApplication(scanBasePackages = {"com.yourapp", "com.commonauth"})
```

and, in a configuration that does **not** apply to a no-database profile:

```java
@EntityScan(basePackages = {"com.yourapp.store.jpa", "com.commonauth.store.jpa"})
@EnableJpaRepositories(basePackages = {"com.yourapp.store.jpa", "com.commonauth.store.jpa"})
```

> **Two traps, both hit while writing this.**
>
> 1. `@EntityScan` and `@EnableJpaRepositories` **replace** Boot's default scan — "the
>    package of the application class, and below" — rather than adding to it. Name only the
>    shared package and the app's *own* repositories vanish. Verified by doing it: the
>    result is `NoSuchBeanDefinitionException` for a repository that is right there on the
>    classpath.
> 2. `@EnableJpaRepositories` switches the repository infrastructure on **regardless of
>    whether the JPA auto-configuration is excluded**. Put it on the application class and a
>    `memory` profile that excludes the DataSource stops booting. That is why both apps keep
>    it in a `@Profile("!memory")` configuration.

Then supply your own route rules, letting the shared hardening do the rest:

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    AuthHardening.applyTo(http, "/api/auth/register", "/api/auth/login");
    http.authorizeHttpRequests(a -> a
            .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
            .anyRequest().authenticated());
    return http.build();
}
```

Your app still needs the `users`, `login_attempts`, `SPRING_SESSION` and
`SPRING_SESSION_ATTRIBUTES` tables in its own migrations — copy them from
`apps/refillradar/src/main/resources/db/migration/`.

## Configuration

All under the `auth.` prefix, so it reads the same in every app. Defaults shown:

```yaml
auth:
  login-throttle:
    max-failures-per-username: 5
    max-failures-per-ip: 20
    window: PT15M
    max-registrations-per-ip: 5
    registration-window: PT1H
```

The reasoning behind those numbers — two counters, a window that heals rather than a
lockout, a refusal that happens before the BCrypt verification — is in
[`apps/refillradar/README.md`](../../apps/refillradar/README.md#v05-rate-limiting-and-the-shape-of-a-guessing-attack).

## Build and deploy

It is published **nowhere**. It is built from this repository, so it must be installed into
the local Maven repository before any app can resolve it:

```bash
mvn -f libs/common-auth install        # 12 tests, no database, no network
```

Every consuming app's CI does this before `mvn verify`, and every consuming app's Dockerfile
does it inside the build stage — which is why **those images build from the repository
root**, not from the app directory:

```bash
docker build -f apps/refillradar/Dockerfile -t refillradar .
```

A build context rooted at `apps/refillradar/` cannot see a sibling `libs/` directory.

## Tests

12 unit tests, all of the login throttle, all against a fixed `Clock` and an in-memory
store. That is the whole point of separating policy from persistence: "the block lifts
fifteen minutes later" is asserted in milliseconds, with no database and no sleeping. The
integration half — real HTTP, real PostgreSQL, real attacks — lives in each consuming app.
