# syntax=docker/dockerfile:1
# Clarity AI backend — Spring Boot (Java 21, Maven) on Render.
#
# Render settings for this repo layout:
#   Root Directory: server
#   Dockerfile Path: ./Dockerfile
#
# No secrets are baked into this image. Gemini and Neon credentials are
# supplied at runtime via environment variables (see server/.env.example).

# ---------------------------------------------------------------------------
# Stage 1: build the executable Spring Boot JAR with Maven.
# Uses the system `mvn` from the image (not the repo wrapper) to avoid
# Windows CRLF / exec-bit issues with mvnw inside Linux containers.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the build descriptor first so dependency downloads are cached
# in their own layer and reused when only application code changes.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2: slim JRE-only runtime.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

# Render injects $PORT at runtime; default to 8080 for local runs.
ENV PORT=8080

WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar

# Run as a non-root user where practical.
RUN groupadd -r appuser && useradd -r -g appuser appuser \
    && chown -R appuser:appuser /app
USER appuser

# Render routes to $PORT; EXPOSE is documentation only.
EXPOSE 8080

# JAVA_OPTS is optional extra JVM flags (e.g. "-Xmx512m").
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
