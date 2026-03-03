FROM maven:3.9.12-eclipse-temurin-17
WORKDIR /tmp
COPY . .
RUN mvn clean verify
CMD mvn spring-boot:run