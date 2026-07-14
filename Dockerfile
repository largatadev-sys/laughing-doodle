# syntax=docker/dockerfile:1
#
# Story 9 / ADR-008 — one image that serves the Expo web app AND the Spring API on a
# single origin. Multi-stage: build the web export, build the jar (web bundled into
# static/), run it on a small JRE as non-root. This is the exact artifact prod (Railway)
# runs; `docker compose --profile fullstack up --build` runs it locally as the parity gate.

# ── Stage 1: build the Expo web export ──────────────────────────────────────────────
# Debian slim (glibc) rather than Alpine (musl) — safer for Expo/Metro's optional native bits.
# Expo SDK 57 requires Node >= 22.13.
FROM node:22-slim AS web
WORKDIR /client
# Deps first, for layer caching (only re-runs when the lockfile changes).
COPY client/package.json client/package-lock.json ./
RUN npm ci
COPY client/ ./
# Empty API base → the client calls a relative /api, i.e. the same origin Spring serves it on.
ENV EXPO_PUBLIC_API_URL=""
RUN npx expo export --platform web --output-dir dist

# ── Stage 2: build the Spring Boot jar (with the web export inside) ──────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# Wrapper + build config first, so the dependency download layer caches on build.gradle only.
# The Java project now lives under backend/ (peer to client/); build context stays the repo root.
COPY backend/gradlew ./
COPY backend/gradle ./gradle
# gradlew is checked out with Windows CRLF line endings; strip them so the shebang works on Linux.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY backend/build.gradle backend/settings.gradle ./
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
# App source + the web export copied into static/ so bootJar packages it at classpath:/static/.
COPY backend/src ./src
COPY --from=web /client/dist ./src/main/resources/static
# Tests need a Docker daemon (Testcontainers) — they run in the dev loop, not the image build.
RUN ./gradlew --no-daemon clean bootJar -x test

# ── Stage 3: runtime ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
# Run as a non-root system user.
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
# Documentation only; the app binds ${PORT:8080} (Railway injects PORT).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
