# syntax=docker/dockerfile:1
# Clarity AI — combined single-service image: React (Vite) frontend + Spring Boot backend.
#
# Build context: repository root.
#   Render settings: Root Directory blank, Dockerfile Path ./Dockerfile,
#   Build Command blank, Start Command blank.
#
# The UI and the API are served from the same URL. The frontend calls the
# backend via relative URLs (VITE_API_BASE_URL unset -> "/api/ai/ask"), so no
# localhost appears in production. No secrets are baked into this image;
# Gemini and Neon credentials come from runtime environment variables
# (see server/.env.example). Local development is unaffected: keep using
# `npm run dev` in client/ with the Vite /api proxy plus the backend on :8080.

# ---------------------------------------------------------------------------
# Stage 1: build the React + Vite frontend with its own lockfile and script.
# ---------------------------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /client

COPY client/package.json client/package-lock.json ./
RUN npm ci

COPY client/ ./
# VITE_API_BASE_URL is intentionally unset so chatApi.ts falls back to ""
# and production requests go to the same-origin relative path /api/ai/ask.
RUN npm run build

# ---------------------------------------------------------------------------
# Stage 2: build the Spring Boot JAR (Java 21, Maven), embedding the
# frontend assets as static resources so one service serves UI + API.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build

# Copy the build descriptor first so dependency downloads are cached
# in their own layer and reused when only application code changes.
COPY server/pom.xml ./
RUN mvn -B dependency:go-offline

COPY server/src ./src

# Package the built UI into Spring Boot's static resources (no source change).
COPY --from=frontend /client/dist ./src/main/resources/static

RUN mvn -B package -DskipTests

# ---------------------------------------------------------------------------
# Stage 3: slim JRE-only runtime.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

# Render injects $PORT at runtime; default to 8080 for local runs.
ENV PORT=8080

WORKDIR /app
COPY --from=backend /build/target/*.jar /app/app.jar

# Run as a non-root user where practical.
RUN groupadd -r appuser && useradd -r -g appuser appuser \
    && chown -R appuser:appuser /app
USER appuser

# Render routes to $PORT; EXPOSE is documentation only.
EXPOSE 8080

# JAVA_OPTS is optional extra JVM flags (e.g. "-Xmx512m").
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
