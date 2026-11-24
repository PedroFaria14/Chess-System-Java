FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends stockfish wget adduser \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system chess && adduser --system --ingroup chess chess
WORKDIR /app
RUN chown chess:chess /app
COPY --from=build --chown=chess:chess /build/target/chess-api-1.0.0.jar app.jar
USER chess
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=6 CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
