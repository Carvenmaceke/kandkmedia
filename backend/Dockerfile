# Multi-stage build: compile with Maven+JDK, run with a slim JRE only.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven --no-install-recommends \
    && mvn -B -q clean package -DskipTests \
    && rm -rf /var/lib/apt/lists/*

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render (and most hosts) set PORT at runtime; Spring reads server.port from
# it via application.yml's ${PORT:8080} — see below.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
