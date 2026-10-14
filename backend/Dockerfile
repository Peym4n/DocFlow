FROM eclipse-temurin:25.0.4.1_1-jdk-jammy@sha256:318f90a80337fb6f26b025dbec0a95fa6f48abf60ce129babf555618356bdbf8 AS build

WORKDIR /workspace/backend

COPY backend/mvnw ./mvnw
COPY backend/.mvn ./.mvn
COPY backend/pom.xml ./pom.xml
COPY backend/apps/api/pom.xml ./apps/api/pom.xml
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY backend/apps/api/src ./apps/api/src
RUN ./mvnw -B -ntp clean package -DskipTests

FROM eclipse-temurin:25.0.4.1_1-jre-jammy@sha256:25777acfabf927084b7ef46d8bc786b6203c8c344f56541054238b8c4fe73db9

# curl is used by the readiness probe; Maven and the compiler stay in the build stage.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 docflow \
    && useradd --system --uid 10001 --gid docflow --home-dir /app --shell /usr/sbin/nologin docflow

WORKDIR /app
COPY --from=build --chown=docflow:docflow /workspace/backend/apps/api/target/api-*.jar ./app.jar
USER docflow

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
