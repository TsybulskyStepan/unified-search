FROM node:22-alpine AS frontend-build

WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci
COPY frontend/ .
RUN npm run build

FROM gradle:9.7.1-jdk25 AS build

WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew --no-daemon dependencies
COPY --from=frontend-build /frontend/dist /workspace/src/main/resources/static
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre

# curl is the compose healthcheck's only dependency (§11.4) — the base image has neither curl nor
# wget, and a hand-rolled /dev/tcp HTTP check is the kind of thing that fails silently in CI.
RUN apt-get update && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
