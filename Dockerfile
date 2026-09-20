FROM node:22-alpine AS frontend-build

WORKDIR /frontend
# Layer 1: resolve dependencies — cached until package.json/package-lock.json change
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci
# Layer 2: copy source and build — busted by any frontend source change
COPY frontend/ .
RUN npm run build

FROM gradle:9.7.1-jdk25 AS build

WORKDIR /workspace
# Layer 3: Gradle wrapper and build files — cached until build.gradle or settings.gradle change
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
# Layer 4: resolve Gradle dependencies — cached until build.gradle dependencies block changes
RUN ./gradlew --no-daemon dependencies
# Layer 5: copy Java source — busted by any backend source change
COPY src src
# Layer 6: copy frontend dist into static resources — busted by any frontend source change
COPY --from=frontend-build /frontend/dist src/main/resources/static
# Layer 7: build the jar — fast incremental step
RUN ./gradlew --no-daemon bootJar -x buildFrontend -x npmInstall -x spotlessCheck -x test

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
