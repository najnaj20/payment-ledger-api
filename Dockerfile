# syntax=docker/dockerfile:1
# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Runtime stage: minimal JRE, non-root user ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r appgroup && useradd -r -g appgroup -u 10001 appuser
COPY --from=build /app/target/payment-ledger-api-0.1.0.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
