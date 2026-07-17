# Pre-built jar is copied into a slim JRE runtime image, so the container build
# needs NO network for Maven (the host runs `mvn package -DskipTests` first).
# The jar picks up infra via SPRING_PROFILES_ACTIVE and the
# ORDER_DB_*/REDIS_*/KAFKA_* env vars injected by docker-compose.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
