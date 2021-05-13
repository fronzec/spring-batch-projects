DB_CONTAINER_ID := $(shell docker compose ps -q db)

# Boostrap our thirdparty services required to run our application
bootstrap:
	docker compose up -d db

# Run our application
run:
	SCOPE=dev mvn clean spring-boot:run

# Allow debug our application attaching the debugguer to the docker container
debug:
	SCOPE=dev mvn clean spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"

stop:
	docker compose stop