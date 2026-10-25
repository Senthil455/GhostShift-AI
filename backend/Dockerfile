# GhostShift AI backend: build the shaded JAR, then run it as a non-root user.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the wrapper and build files first so dependency resolution can be cached.
COPY gradlew gradlew.bat settings.gradle gradle.properties build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN ./gradlew shadowJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin ghostshift \
    && mkdir -p /data \
    && chown -R ghostshift:ghostshift /app /data

COPY --from=build /workspace/build/libs/ghostshift-ai-1.0.0-all.jar /app/ghostshift-ai.jar

ENV GHOSTSHIFT_HOST=0.0.0.0 \
    GHOSTSHIFT_PORT=8080 \
    GHOSTSHIFT_DATA_DIR=/data

USER ghostshift
EXPOSE 8080
VOLUME ["/data"]

ENTRYPOINT ["java", "-jar", "/app/ghostshift-ai.jar"]
