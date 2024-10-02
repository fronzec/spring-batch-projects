# Example Batch service

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

## How to set up mock service powered by mockintosh
1. Fist install `pipx` to isolate `mockintosh` environment
2. Install `mockintosh` using pipx `pipx install mockintosh`. Latest version `0.13.17`
3. Sometimetimes mockintosh venv doesn't work correctly by incompatible version of `markupsafe`, install a compatible version with the 
   mockintosh version using `pipx inject mockintosh markupsafe==2.0.1`
4. Test using `mockintosh --version` command
