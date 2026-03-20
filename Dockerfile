# ========================== STAGE 1: BUILD ==========================
# Uses a Maven image that comes with both Maven and JDK pre-installed.
# This stage is discarded after the build — only the JAR is carried forward,
# keeping the final image small and free of build tools.
FROM maven:3.9-eclipse-temurin-25 AS build

# Sets /app as the working directory inside the container.
# All subsequent commands (COPY, RUN, etc.) will execute relative to this path.
WORKDIR /app

# Copies only the project descriptor (pom.xml) first.
# Docker caches each layer — by copying this BEFORE the source code,
# the dependency-download layer is cached and only re-runs when pom.xml changes.
COPY pom.xml .

# Downloads all project dependencies to the local Maven repository.
# The -B flag enables non-interactive (batch) mode, suppressing download
# progress output for cleaner build logs.
RUN mvn dependency:go-offline -B

# Copies the application source code into the container.
# This layer is only rebuilt when source files change — dependencies stay cached.
COPY src src

# Compiles the application and packages it into an executable JAR file.
# -DskipTests skips test execution (tests should run in CI, not during image build).
# -B enables batch mode for cleaner output.
RUN mvn package -DskipTests -B


# ========================== STAGE 2: RUNTIME ==========================
# Uses a slim JRE-only image — no compiler, no build tools, no source code.
# This minimizes the attack surface and reduces the final image size significantly.
FROM eclipse-temurin:25-jre

# Creates a non-root user and group for running the application.
# Running as root inside a container is a security risk — a compromised process
# would have full control of the container filesystem and could potentially
# escape to the host. Industry best practice is to always run as a non-root user.
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

# Sets /app as the working directory for the runtime stage.
WORKDIR /app

# Creates the logs directory and gives ownership to the non-root user,
# so the application can write log files without permission errors.
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# Copies the built JAR from the build stage into the runtime image.
# This is the only artifact carried over — no source code, no build tools.
COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

# Switches to the non-root user for all subsequent commands and the ENTRYPOINT.
USER appuser

# Documents that the application listens on port 8080 at runtime.
# This is metadata only — the port must still be mapped via docker-compose or -p flag.
EXPOSE 8080

# Defines the command that runs when the container starts.
# Uses exec form (JSON array) so the Java process receives OS signals (e.g., SIGTERM)
# directly, enabling graceful shutdown instead of abrupt termination.
ENTRYPOINT ["java", "-jar", "app.jar"]
