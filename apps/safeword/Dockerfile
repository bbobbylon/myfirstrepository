# ---- Stage 1: build ----------------------------------------------------------
# A multi-stage build. This stage has Maven, the JDK and the full dependency
# cache - hundreds of megabytes we do NOT want in the shipped image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# NOTE THE BUILD CONTEXT. This image is built from the REPOSITORY ROOT, not from this
# directory:
#
#     docker build -f apps/safeword/Dockerfile -t safeword .
#
# because the application depends on libs/common-auth, and a build context rooted at
# apps/safeword/ cannot see a sibling directory. Build it the old way and Maven fails
# resolving com.commonauth:common-auth - it is published nowhere, it is built here.

# The shared module first. It changes far less often than the application, so this layer
# stays cached across ordinary edits, and installing it into the local repository is what
# lets the next step resolve it.
COPY libs/common-auth libs/common-auth
RUN mvn -B -f libs/common-auth/pom.xml install -DskipTests

# Then the application's POM alone, so dependency resolution is its own cached layer.
# Copying the sources up front would re-download the internet on every single edit.
COPY apps/safeword/pom.xml apps/safeword/pom.xml
RUN mvn -B -f apps/safeword/pom.xml dependency:go-offline

COPY apps/safeword/src apps/safeword/src
RUN mvn -B -f apps/safeword/pom.xml clean package -DskipTests

# ---- Stage 2: runtime --------------------------------------------------------
# Only the JRE, and only the finished JAR. Nothing from stage 1 comes along
# unless explicitly copied, so the build toolchain never reaches production.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as a non-root user. If the application is ever compromised, the attacker
# lands as an unprivileged account rather than as root inside the container.
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/apps/safeword/target/safeword-*.jar app.jar

EXPOSE 8083


ENTRYPOINT ["java", "-jar", "app.jar"]
