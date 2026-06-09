# Stage 1: Build fat JAR with Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Download dependencies before copying source for layer caching
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn clean package -DskipTests -q

# Stage 2: Minimal JRE runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# Install wget for HEALTHCHECK (alpine JRE image does not include it)
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/batch-service/actuator/health || exit 1
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-XX:MaxMetaspaceSize=256m", "-jar", "app.jar"]
