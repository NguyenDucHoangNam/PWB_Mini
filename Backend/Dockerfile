# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Poms first: the dependency layer is rebuilt only when a pom changes, not on every source edit.
COPY pom.xml ./
COPY shared/shared-kernel/pom.xml shared/shared-kernel/
COPY shared/shared-web/pom.xml shared/shared-web/
COPY shared/shared-infrastructure/pom.xml shared/shared-infrastructure/
COPY modules/iam/pom.xml modules/iam/
COPY modules/audio/pom.xml modules/audio/
COPY modules/liveroom/pom.xml modules/liveroom/
COPY bootstrap/pom.xml bootstrap/
RUN mvn -B -ntp dependency:go-offline -DskipTests || true

COPY shared shared
COPY modules modules
COPY bootstrap bootstrap

# `-Dmaven.test.skip=true`, not `-DskipTests`: the latter skips *running* tests but still compiles
# them, so an image build fails on any test source that no longer compiles — and one in modules/iam
# references a class deleted in caaa5a1. Compiling tests to build a runtime image is wasted work
# regardless; the test suite belongs in CI, where a failure should stop a merge rather than a deploy.
RUN mvn -B -ntp package -Dmaven.test.skip=true

# ---------------------------------------------------------------------------
# Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# FFmpeg renders the watermarked audio; ffprobe reads the song and voice tag durations that the
# filtergraph is built from. Both ship in this package — the application shells out to them by name.
RUN apk add --no-cache ffmpeg \
    && ffmpeg -version | head -1 \
    && ffprobe -version | head -1

RUN addgroup -S pwb && adduser -S pwb -G pwb

WORKDIR /app
COPY --from=build /build/bootstrap/target/pwb-bootstrap-*.jar app.jar

# Scratch space for audio jobs. Sized for one job at a time (source + voice tag + output), since the
# processing consumer runs with a concurrency of one. Mount a volume here if the container filesystem
# is small or read-only.
ENV PWB_AUDIO_PROCESSOR_WORKING_DIR=/var/tmp/pwb-audio
RUN mkdir -p "$PWB_AUDIO_PROCESSOR_WORKING_DIR" \
    && chown -R pwb:pwb /app "$PWB_AUDIO_PROCESSOR_WORKING_DIR"

USER pwb
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
