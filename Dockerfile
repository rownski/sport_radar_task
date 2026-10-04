FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
# PostgreSQL integration tests run separately via mvn verify on a Docker-capable host.
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S scoreboard && adduser -S scoreboard -G scoreboard
WORKDIR /app
COPY --from=build /workspace/target/wc_score_board-1.0-SNAPSHOT.jar app.jar
USER scoreboard
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
