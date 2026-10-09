# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot jar, then split it into layers ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Resolve dependencies first so they are cached until pom.xml changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/walletflow-api.jar extract --layers --launcher --destination target/extracted

# ---- Runtime stage: JRE only, non-root, layered for small incremental pulls ----
FROM eclipse-temurin:17-jre
RUN groupadd --system walletflow && useradd --system --gid walletflow --no-create-home walletflow
WORKDIR /app

COPY --from=build /build/target/extracted/dependencies/ ./
COPY --from=build /build/target/extracted/spring-boot-loader/ ./
COPY --from=build /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build /build/target/extracted/application/ ./

USER walletflow:walletflow
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
