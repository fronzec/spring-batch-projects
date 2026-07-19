# Spring Batch Plugin Lab

A monorepo for experimenting with Spring Batch: a dynamic plugin host service, four batch job plugins, and a Svelte ops UI.

## Module Map

| Module | What it is |
|--------|-----------|
| `batch-job-api` | Shared API contract (interfaces + annotations) published to GitHub Packages; plugins and the host both depend on it |
| `fr-batch-service` | Spring Boot 4 / Spring Batch 6 host — loads plugins dynamically from uploaded JARs, exposes a REST management API, uses MySQL with Flyway migrations |
| `ticket-pdf-job` | Plugin: generates PDF tickets for events |
| `ticket-bundle-job` | Plugin: bundles generated ticket PDFs |
| `fault-tolerant-harvester-job` | Plugin: fault-tolerant harvest pipeline with retry and dead-letter support |
| `partitioned-harvester-job` | Plugin: partitioned (multi-threaded) harvest pipeline |
| `batch-ops-ui` | Svelte + Vite ops dashboard — manages plugin lifecycle and triggers jobs via the backend REST API |

## Running Locally

See **[RUNNING_LOCALLY.md](RUNNING_LOCALLY.md)** for the full end-to-end guide: MySQL setup, backend startup, seed data, plugin build + upload, and UI launch.

## Analytics

![Repobeats](https://repobeats.axiom.co/api/embed/4c25e73ac0dba14cbc5ce187c36e5963a85e58a8.svg "Repobeats analytics image")
