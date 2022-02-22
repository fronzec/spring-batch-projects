FROM openjdk:11 AS builder
RUN mkdir /code
COPY .mvn /code/.mvn
COPY mvnw /code
COPY mvnw.cmd /code
COPY pom.xml /code
COPY src/ /code/src
WORKDIR /code
RUN ./mvnw clean install package -DskipTests=true

FROM openjdk:11-jre-slim
RUN mkdir /code
COPY --from=builder /code/target /code
EXPOSE 8080
ENTRYPOINT [ "sh", "-c", "java -jar -Duser.timezone=$TIMEZONE -Dnetworkaddress.cache.ttl=60 -Dnetworkaddress.cache.negative.ttl=30   /code/*.jar"]