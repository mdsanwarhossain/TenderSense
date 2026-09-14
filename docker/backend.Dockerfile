# --- Build stage -------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN ./gradlew --no-daemon bootJar -x test

# --- Runtime stage -------------------------------------------------------------
# Debian, not the default Ubuntu-based temurin tag and not Alpine:
#   - Ubuntu dropped the real Chromium deb years ago in favor of a snap stub, which
#     can't launch inside a container (no snapd).
#   - Alpine's musl libc can't load onnxruntime's glibc-linked native library, which
#     spring-ai-starter-model-transformers needs for local embeddings.
# Debian still ships a genuine, version-matched chromium/chromium-driver pair, and
# has glibc, so the JRE is copied in from the official Temurin image instead of
# pulled from Debian's own (JDK-17-only-on-bookworm) apt repo.
FROM eclipse-temurin:21-jre AS jre

FROM debian:bookworm-slim
WORKDIR /app

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre /opt/java/openjdk /opt/java/openjdk

# --no-sandbox/--disable-dev-shm-usage are already set in EgpTenderFetchServiceImpl,
# so no extra container flags are needed here.
RUN apt-get update \
    && apt-get install -y --no-install-recommends chromium chromium-driver \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
