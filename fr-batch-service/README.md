# Single threaded batch service

## Tech Stack

- Java 17
- Spring Framework [multiple modules]
- MySQL as DB

## How to run locally

### Requeriments

- Your favorite IDE: I prefer IntelliJ or VsCode
- Java 17 SDK
- Maven 3, also you can use Maven Wrapper
- Mysql 5.7.x
- Some HTTP Rest Client: e.g. Postman
- Some mocking service alternative: e.g. Mockoon or Mockintosh


### Run with IntelliJ

Pending ...

### Run with VS Code

Pending ...

## How to develop

## Spring batch Jobs

## Http Web endpoints

## Roadmap for development experience

- [ ] Easy dev environment setup using docker, docker compose or podman and podman compose, using maven wrapper and
  reusing local m2 artifacs. Some limitations found here was the resources limitations for the DB container.
  - [x] Development environment using gitpod

## Roadmap for features included

- [ ] Allow run list of jobs sync and async
- [ ] At least one complex job using csv to db and db to rest endpoint
- [ ] Thirdparty services mocking using mockintosh
- [ ] Metrics with prometheus and grafana
- [ ] Web admin using svelte or react
