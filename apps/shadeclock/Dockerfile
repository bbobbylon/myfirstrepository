# ---- Stage 1: build ----------------------------------------------------------
# A multi-stage build. This stage has Maven, the JDK and the full dependency
# cache - hundreds of megabytes we do NOT want in the shipped image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM alone first, then resolve dependencies. Docker caches each layer,
# so as long as pom.xml is unchanged this expensive step is reused even when the
# source changes. Copying everything up front would re-download the internet on
# every single edit.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Stage 2: runtime --------------------------------------------------------
# Only the JRE, and only the finished JAR. Nothing from stage 1 comes along
# unless explicitly copied, so the build toolchain never reaches production.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as a non-root user. If the application is ever compromised, the attacker
# lands as an unprivileged account rather than as root inside the container.
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/target/shadeclock-*.jar app.jar

EXPOSE 8081

# Default to the fixture source so a container started with no configuration
# serves obviously-fake data rather than pretending to be live.
ENV SHADECLOCK_FORECAST_SOURCE=fixture

ENTRYPOINT ["java", "-jar", "app.jar"]
