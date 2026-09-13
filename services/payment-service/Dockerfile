# syntax=docker/dockerfile:1

# ---- build: compile and package the jar with Maven ----------------------------------
# pom.xml targets Java 25, so the JDK in this stage has to be 25 as well.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
# Tests are skipped here: the integration tests need Docker (Testcontainers) and belong in
# CI, not inside an image build.
RUN mvn -B -q -DskipTests package

# ---- run: only a JRE and the jar ------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app

# A fixed, numeric, unprivileged user: Kubernetes' runAsNonRoot can only verify a number.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

COPY --from=build --chown=10001:10001 /workspace/target/payment-service-*.jar /app/app.jar

USER 10001:10001
EXPOSE 8080

# Exec form: java is PID 1 and receives SIGTERM directly, so graceful shutdown works.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
