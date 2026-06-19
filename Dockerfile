# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# WerewolfHelper — single self-contained image.
#
# The production artifact is one bootJar that serves the React SPA from
# classpath:/static (see backend/build.gradle.kts + config/SpaConfig.kt). We
# build it in three stages so frontend/backend deps cache independently:
#
#   1. frontend  — yarn build → frontend/dist
#   2. backend   — copy that dist into resources/static, then bootJar with
#                  -PskipFrontend so Gradle doesn't re-run yarn (no Node in the
#                  JDK image). Spring picks the SPA up from classpath:/static.
#   3. runtime   — JRE 25 + the fat jar only.
# ---------------------------------------------------------------------------

# --- 1. Frontend (React 19 · Vite · Yarn classic) --------------------------
FROM node:24-slim AS frontend
WORKDIR /frontend

# Deps layer: only invalidated when the manifests change.
COPY frontend/package.json frontend/yarn.lock ./
RUN yarn install --frozen-lockfile

COPY frontend/ ./
RUN yarn build          # tsc --noEmit && vite build → /frontend/dist


# --- 2. Backend (Kotlin · Spring Boot 4 · Gradle, JDK 25) ------------------
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app

# Gradle wrapper + build scripts first so the dependency layer caches across
# source-only changes. gradlew is normalised to LF (it may be CRLF when the
# build context comes from a Windows checkout).
COPY backend/gradlew backend/gradlew.bat backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY backend/src ./src
# The SPA goes on the classpath so bootJar can be built with -PskipFrontend
# (no Node toolchain needed in this stage).
COPY --from=frontend /frontend/dist ./src/main/resources/static

# Cache mounts keep Gradle/Yarn-free deps warm across local rebuilds.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -PskipFrontend clean bootJar \
    && cp build/libs/*.jar app.jar


# --- 3. Runtime (JRE 25, non-root) -----------------------------------------
# Debian/Ubuntu-based (glibc), NOT Alpine: JDA's lavaplayer ships native libs
# (jdave-native-linux-*) that need glibc.
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --home /app app

COPY --from=backend /app/app.jar app.jar
USER app

ENV JAVA_OPTS="" \
    PORT=8080
EXPOSE 8080

# SPA fallback returns index.html (200) for any path, so / proves the web tier
# is serving even before a game exists.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://localhost:${PORT}/" >/dev/null || exit 1

# --enable-native-access mirrors bootRun's jvmArgs (suppresses JDK 25 native
# access warnings from the audio stack).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS --enable-native-access=ALL-UNNAMED -jar app.jar"]
