# syntax=docker/dockerfile:1.7

# ----- Stage 1: build the bootable jar with Gradle -----
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle build.gradle gradle.properties ./
RUN chmod +x gradlew && ./gradlew --version

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew clean bootJar -x test --no-daemon

# ----- Stage 2: explode the layered jar so Docker can cache by layer -----
FROM eclipse-temurin:25-jre-noble AS extractor
WORKDIR /extract
COPY --from=builder /workspace/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ----- Stage 3: minimal runtime image -----
FROM eclipse-temurin:25-jre-noble
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 1001 library \
 && useradd  --system --uid 1001 --gid library --home /app --shell /usr/sbin/nologin library

COPY --from=extractor --chown=library:library /extract/extracted/dependencies/         ./
COPY --from=extractor --chown=library:library /extract/extracted/spring-boot-loader/   ./
COPY --from=extractor --chown=library:library /extract/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=library:library /extract/extracted/application/          ./

USER library

EXPOSE 8080

ENV JAVA_OPTS="" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher \"$@\"", "--"]
