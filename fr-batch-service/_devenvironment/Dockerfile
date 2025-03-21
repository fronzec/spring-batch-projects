FROM maven:3.9.9-eclipse-temurin-17
WORKDIR /tmp
COPY . .
RUN mvn clean verify
CMD mvn spring-boot:run