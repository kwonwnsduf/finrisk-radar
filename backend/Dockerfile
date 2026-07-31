# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S finrisk && adduser -S -G finrisk finrisk
COPY --from=builder --chown=finrisk:finrisk /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS=""

USER finrisk
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=10 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
