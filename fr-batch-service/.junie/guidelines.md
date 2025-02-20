# Example Batch Service - Project Guidelines

## Project Overview

This is a Spring Batch-based service that demonstrates the implementation of batch processing jobs with modern technologies and best practices.

### Purpose

The service is designed to:
- Execute batch jobs both synchronously and asynchronously
- Process data transformations (CSV to DB, DB to REST endpoints)
- Integrate with third-party services
- Provide monitoring and administration capabilities

### Technical Stack

- **Core Technologies**:
  - Java 21
  - Spring Framework (multiple modules)
  - Spring Batch
  - MySQL 8.0.23 (Database)

- **Development Tools**:
  - Maven 3 (with Maven Wrapper support)
  - Docker/Podman for containerization
  - Mockintosh for service mocking
  - Prometheus & Grafana for metrics

### Architecture Overview

#### Job Management System

The service implements a sophisticated job management system with the following components:

1. **Jobs Table**:
   - Stores core job configurations
   - Manages Spring bean references
   - Tracks job metadata (creation, updates)

2. **Job Definitions**:
   - Links to core jobs
   - Supports country-specific configurations (MX, USA)
   - Implements status management (enabled, disabled, archived)
   - Controls execution parameters (e.g., past-day execution capability)

3. **Job Parameters**:
   - Flexible parameter management for job customization
   - Name-value pair storage for job configurations

### Development Guidelines

1. **Environment Setup**:
   - Use IDE (IntelliJ or VS Code recommended)
   - Ensure Java 21 SDK is installed
   - Set up MySQL 8.0.23
   - Configure HTTP Rest Client (e.g., Postman)
   - Set up mocking service (Mockoon or Mockintosh)

2. **Development Environment Options**:
   - Local development setup
   - Gitpod environment (recommended for quick starts)
   - Docker/Podman based setup (in progress)

3. **Mocking Service Setup**:
   ```
   1. Install pipx
   2. Install mockintosh via pipx
   3. Configure markupsafe compatibility if needed
   4. Verify installation with mockintosh --version
   ```

### Roadmap

1. **Development Experience**:
   - [x] Gitpod environment setup
   - [ ] Docker/Podman based development environment

2. **Features**:
   - [ ] Synchronous and asynchronous job execution
   - [ ] Complex job implementation (CSV → DB → REST)
   - [ ] Third-party service mocking
   - [ ] Metrics implementation
   - [ ] Web admin interface (Svelte/React)

### Best Practices

1. **Job Implementation**:
   - Follow Spring Batch patterns
   - Implement proper error handling
   - Include job parameters validation
   - Add appropriate logging

2. **Code Quality**:
   - Write comprehensive tests
   - Follow Spring best practices
   - Maintain proper documentation
   - Use consistent code formatting

3. **Monitoring and Maintenance**:
   - Implement health checks
   - Set up proper logging
   - Configure metrics collection
   - Regular dependency updates
