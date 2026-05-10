# ADR 001: Dynamic Job Loading System with Database-Driven Registry

## Status
**Proposed** - 2026-03-02

## Context and Problem Statement

The current fr-batch-service implementation has several limitations that hinder scalability and operational flexibility:

### Current Limitations

1. **Hardcoded Job Registry**: Jobs are manually registered in `JobsManagerService.manualDefinedJobs` HashMap, requiring code changes and redeployment for new jobs
2. **Monolithic Architecture**: All job logic resides in the same codebase, making it difficult for teams to work independently
3. **No Version Management**: Cannot run multiple versions of the same job or roll back to previous versions
4. **Tight Coupling**: Job implementations are tightly coupled to the core batch service
5. **Limited Reusability**: Jobs cannot be shared across different batch service instances or environments
6. **Manual Deployment**: Adding new jobs requires full application rebuild and deployment

### Business Drivers

- **Faster Time-to-Market**: Business teams need to deploy new batch jobs without waiting for core platform releases
- **Team Autonomy**: Multiple teams should be able to develop and deploy their own batch jobs independently
- **Flexibility**: Ability to enable/disable jobs dynamically based on business needs
- **Operational Safety**: Ability to test jobs in isolation and roll back problematic versions
- **Multi-tenancy**: Support for different job sets per environment or customer

## Decision

We will implement a **Plugin-Based Dynamic Job Loading System** that allows batch jobs to be:

1. Developed as separate, independent Maven/Gradle projects
2. Packaged as executable JAR files
3. Uploaded and registered dynamically at runtime
4. Stored and managed through a database registry
5. Loaded, unloaded, and reloaded without service restart

### Key Components

```
┌─────────────────────────────────────────────────────────────┐
│                   External Job Plugins                       │
│  (Separate Git Repos, Independent Build Lifecycle)          │
│                                                              │
│  job-payment-processing/     job-report-generation/         │
│  job-data-migration/          job-reconciliation/           │
│                                                              │
│  Each implements: BatchJobPlugin interface                  │
│  Each builds to: job-{name}-{version}.jar                   │
└─────────────────────────────────────────────────────────────┘
                            ↓ Upload
┌─────────────────────────────────────────────────────────────┐
│              Job Management REST API                         │
│  POST /job-registry/upload        - Upload JAR             │
│  POST /job-registry/register      - Register job           │
│  PUT  /job-registry/{name}/enable - Enable/disable         │
│  POST /job-registry/{name}/reload - Hot reload             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              Database Job Registry                           │
│                                                              │
│  job_definitions                                            │
│  ├─ id, job_name, version                                   │
│  ├─ jar_file_path, jar_checksum                            │
│  ├─ main_class_name                                         │
│  ├─ enabled, auto_start                                     │
│  └─ created_at, updated_at                                  │
│                                                              │
│  job_parameters_template                                    │
│  ├─ job_definition_id                                       │
│  ├─ param_key, param_type                                   │
│  ├─ default_value, required                                 │
│  └─ description                                             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│         Dynamic Job Loader Service                          │
│                                                              │
│  ┌────────────────────────────────────────┐                │
│  │  DynamicJobClassLoader (per job)       │                │
│  │  - Parent-last delegation              │                │
│  │  - Isolated class space                │                │
│  │  - Proper cleanup on unload            │                │
│  └────────────────────────────────────────┘                │
│                                                              │
│  ┌────────────────────────────────────────┐                │
│  │  JobRegistryService                    │                │
│  │  - Scan JAR for BatchJobPlugin         │                │
│  │  - Validate job structure              │                │
│  │  - Register beans with Spring          │                │
│  │  - Track loaded jobs                   │                │
│  └────────────────────────────────────────┘                │
│                                                              │
│  ┌────────────────────────────────────────┐                │
│  │  ClassLoaderManager                    │                │
│  │  - Manage classloader lifecycle        │                │
│  │  - Handle unload/reload                │                │
│  │  - Prevent memory leaks                │                │
│  └────────────────────────────────────────┘                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│         Spring Batch Job Execution                          │
│  (Existing JobsManagerService with minor refactoring)       │
└─────────────────────────────────────────────────────────────┘
```

## Technical Architecture

### 1. Job Plugin Contract (batch-job-api module)

Create a separate API module that defines the contract for job plugins:

```java
// batch-job-api/src/main/java/com/fronzec/api/BatchJobPlugin.java
public interface BatchJobPlugin {
    /**
     * Unique identifier for the job
     */
    String getJobName();

    /**
     * Semantic version of this job implementation
     */
    String getVersion();

    /**
     * Configure and return the Spring Batch Job
     */
    Job configureJob(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        ApplicationContext parentContext
    );

    /**
     * Default parameters for this job
     */
    Map<String, String> getDefaultParameters();

    /**
     * List of bean names/types required from parent context
     */
    List<String> getRequiredDependencies();

    /**
     * Metadata about the job
     */
    JobMetadata getMetadata();
}

public interface JobMetadata {
    String getDisplayName();
    String getDescription();
    String getAuthor();
    List<String> getTags();
    Duration getEstimatedRuntime();
}
```

### 2. Database Schema

```sql
-- Job registry
CREATE TABLE job_definitions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_name VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    description TEXT,
    version VARCHAR(50) NOT NULL,
    jar_file_path VARCHAR(500) NOT NULL,
    jar_checksum VARCHAR(64) NOT NULL,
    main_class_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT true,
    auto_start BOOLEAN DEFAULT false,
    load_status VARCHAR(50), -- LOADED, UNLOADED, FAILED, LOADING
    load_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    INDEX idx_job_name (job_name),
    INDEX idx_enabled (enabled),
    INDEX idx_load_status (load_status)
);

-- Parameter templates
CREATE TABLE job_parameters_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_definition_id BIGINT NOT NULL,
    param_key VARCHAR(100) NOT NULL,
    param_type VARCHAR(50) NOT NULL, -- STRING, LONG, DOUBLE, DATE, BOOLEAN
    default_value VARCHAR(255),
    required BOOLEAN DEFAULT false,
    description TEXT,
    validation_regex VARCHAR(500),
    FOREIGN KEY (job_definition_id) REFERENCES job_definitions(id) ON DELETE CASCADE,
    UNIQUE KEY uk_job_param (job_definition_id, param_key)
);

-- Job execution history (extends Spring Batch tables)
CREATE TABLE job_executions_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_definition_id BIGINT NOT NULL,
    job_execution_id BIGINT NOT NULL,
    job_version VARCHAR(50),
    execution_metadata JSON,
    FOREIGN KEY (job_definition_id) REFERENCES job_definitions(id),
    FOREIGN KEY (job_execution_id) REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
);
```

### 3. Dynamic ClassLoader Strategy

**Parent-Last Delegation Model:**

```
Parent ClassLoader (fr-batch-service)
    ↑ (only for core classes)
    |
Job ClassLoader (isolated per job)
    ↓ (first looks in job JAR)
Job JAR Classes
```

**Key Features:**
- Each job gets its own `URLClassLoader` instance
- Jobs can use different versions of libraries
- Spring core classes loaded from parent to share infrastructure
- Proper cleanup to prevent memory leaks

```java
public class DynamicJobClassLoader extends URLClassLoader {
    private final String jobName;
    private final ClassLoader parent;
    private final Set<String> sharedPackages;

    public DynamicJobClassLoader(URL[] urls, ClassLoader parent, String jobName) {
        super(urls, parent);
        this.jobName = jobName;
        this.parent = parent;
        // Core Spring packages always delegated to parent
        this.sharedPackages = Set.of(
            "org.springframework.",
            "jakarta.",
            "org.slf4j.",
            "com.fronzec.api." // Job plugin API
        );
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        // Delegate core packages to parent
        if (sharedPackages.stream().anyMatch(name::startsWith)) {
            return parent.loadClass(name);
        }

        // Parent-last: try to load from this classloader first
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                try {
                    c = findClass(name);
                } catch (ClassNotFoundException e) {
                    c = parent.loadClass(name);
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    public void cleanup() {
        try {
            close();
            // Clear any ThreadLocals
            ThreadLocal.class.getDeclaredField("threadLocals").set(null, null);
        } catch (Exception e) {
            // Log error
        }
    }
}
```

### 4. Job Loading Service

```java
@Service
@Slf4j
public class DynamicJobLoaderService {

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobRegistry jobRegistry;
    private final BeanFactory beanFactory;
    private final Map<String, DynamicJobClassLoader> classLoaders = new ConcurrentHashMap<>();
    private final Map<String, BatchJobPlugin> loadedPlugins = new ConcurrentHashMap<>();

    /**
     * Load job from JAR file
     */
    public LoadResult loadJobFromJar(Long jobDefinitionId) {
        JobDefinition jobDef = jobDefinitionRepository.findById(jobDefinitionId)
            .orElseThrow(() -> new JobNotFoundException(jobDefinitionId));

        try {
            // Validate JAR
            File jarFile = new File(jobDef.getJarFilePath());
            validateJar(jarFile, jobDef.getJarChecksum());

            // Create classloader
            URL jarUrl = jarFile.toURI().toURL();
            DynamicJobClassLoader classLoader = new DynamicJobClassLoader(
                new URL[]{jarUrl},
                this.getClass().getClassLoader(),
                jobDef.getJobName()
            );

            // Discover plugin implementation
            BatchJobPlugin plugin = discoverPlugin(classLoader, jobDef.getMainClassName());

            // Validate plugin
            validatePlugin(plugin, jobDef);

            // Register with Spring
            Job springBatchJob = plugin.configureJob(
                beanFactory.getBean(JobRepository.class),
                beanFactory.getBean(PlatformTransactionManager.class),
                (ApplicationContext) beanFactory
            );

            // Register in job registry
            if (jobRegistry instanceof RunnableJobRegistry) {
                ((RunnableJobRegistry) jobRegistry).register(
                    new RunnableJob(springBatchJob)
                );
            }

            // Track loaded job
            classLoaders.put(jobDef.getJobName(), classLoader);
            loadedPlugins.put(jobDef.getJobName(), plugin);

            // Update database status
            jobDef.setLoadStatus(LoadStatus.LOADED);
            jobDef.setLoadError(null);
            jobDefinitionRepository.save(jobDef);

            log.info("Successfully loaded job: {} version {}",
                jobDef.getJobName(), jobDef.getVersion());

            return LoadResult.success(jobDef.getJobName());

        } catch (Exception e) {
            log.error("Failed to load job: {}", jobDef.getJobName(), e);
            jobDef.setLoadStatus(LoadStatus.FAILED);
            jobDef.setLoadError(e.getMessage());
            jobDefinitionRepository.save(jobDef);
            return LoadResult.failure(jobDef.getJobName(), e.getMessage());
        }
    }

    /**
     * Unload job and cleanup resources
     */
    public void unloadJob(String jobName) {
        BatchJobPlugin plugin = loadedPlugins.remove(jobName);
        DynamicJobClassLoader classLoader = classLoaders.remove(jobName);

        if (classLoader != null) {
            // Unregister from Spring
            if (jobRegistry instanceof RunnableJobRegistry) {
                ((RunnableJobRegistry) jobRegistry).unregister(jobName);
            }

            // Cleanup classloader
            classLoader.cleanup();

            // Force GC to reclaim memory
            System.gc();
        }
    }

    /**
     * Reload job (unload + load)
     */
    public LoadResult reloadJob(String jobName) {
        JobDefinition jobDef = jobDefinitionRepository.findByJobName(jobName)
            .orElseThrow(() -> new JobNotFoundException(jobName));

        unloadJob(jobName);
        return loadJobFromJar(jobDef.getId());
    }

    private BatchJobPlugin discoverPlugin(ClassLoader classLoader, String mainClassName)
            throws Exception {
        // Try explicit class name first
        if (mainClassName != null && !mainClassName.isEmpty()) {
            Class<?> pluginClass = classLoader.loadClass(mainClassName);
            return (BatchJobPlugin) pluginClass.getDeclaredConstructor().newInstance();
        }

        // Fall back to Service Provider Interface discovery
        ServiceLoader<BatchJobPlugin> loader = ServiceLoader.load(
            BatchJobPlugin.class,
            classLoader
        );

        Iterator<BatchJobPlugin> iterator = loader.iterator();
        if (!iterator.hasNext()) {
            throw new PluginNotFoundException("No BatchJobPlugin implementation found");
        }

        return iterator.next();
    }

    private void validateJar(File jarFile, String expectedChecksum) throws IOException {
        if (!jarFile.exists()) {
            throw new FileNotFoundException("JAR file not found: " + jarFile);
        }

        // Validate checksum
        String actualChecksum = calculateSHA256(jarFile);
        if (!actualChecksum.equals(expectedChecksum)) {
            throw new SecurityException("JAR checksum mismatch");
        }
    }

    private void validatePlugin(BatchJobPlugin plugin, JobDefinition jobDef) {
        // Validate job name matches
        if (!plugin.getJobName().equals(jobDef.getJobName())) {
            throw new ValidationException("Job name mismatch");
        }

        // Validate required dependencies are available
        for (String dependency : plugin.getRequiredDependencies()) {
            if (!beanFactory.containsBean(dependency)) {
                throw new DependencyNotFoundException(
                    "Required bean not found: " + dependency
                );
            }
        }
    }

    private String calculateSHA256(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return Hex.encodeHexString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### 5. REST API for Job Management

```java
@RestController
@RequestMapping("/api/batch-service/job-registry")
@Slf4j
public class JobManagementController {

    private final DynamicJobLoaderService jobLoaderService;
    private final JobDefinitionRepository jobDefinitionRepository;

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadJobJar(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobName") String jobName,
            @RequestParam("version") String version,
            @RequestParam(value = "mainClassName", required = false) String mainClassName) {

        // Validate file
        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".jar")) {
            return ResponseEntity.badRequest()
                .body(UploadResponse.error("Invalid JAR file"));
        }

        try {
            // Save file to storage
            String fileName = String.format("job-%s-%s.jar", jobName, version);
            Path storagePath = Paths.get("/var/batch-jobs/", fileName);
            Files.copy(file.getInputStream(), storagePath, StandardCopyOption.REPLACE_EXISTING);

            // Calculate checksum
            String checksum = calculateChecksum(storagePath.toFile());

            // Save to database
            JobDefinition jobDef = new JobDefinition();
            jobDef.setJobName(jobName);
            jobDef.setVersion(version);
            jobDef.setJarFilePath(storagePath.toString());
            jobDef.setJarChecksum(checksum);
            jobDef.setMainClassName(mainClassName);
            jobDef.setEnabled(false); // Disabled by default until explicitly enabled
            jobDef.setLoadStatus(LoadStatus.UNLOADED);

            jobDefinitionRepository.save(jobDef);

            return ResponseEntity.ok(UploadResponse.success(jobDef.getId()));

        } catch (Exception e) {
            log.error("Failed to upload job JAR", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(UploadResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> registerJob(@RequestBody JobRegistrationRequest request) {
        JobDefinition jobDef = new JobDefinition();
        jobDef.setJobName(request.getJobName());
        jobDef.setDisplayName(request.getDisplayName());
        jobDef.setDescription(request.getDescription());
        jobDef.setVersion(request.getVersion());
        jobDef.setJarFilePath(request.getJarFilePath());
        jobDef.setJarChecksum(request.getChecksum());
        jobDef.setMainClassName(request.getMainClassName());
        jobDef.setEnabled(false);

        jobDefinitionRepository.save(jobDef);

        return ResponseEntity.ok(RegisterResponse.success(jobDef.getId()));
    }

    @PutMapping("/{jobName}/enable")
    public ResponseEntity<StatusResponse> enableJob(@PathVariable String jobName) {
        JobDefinition jobDef = jobDefinitionRepository.findByJobName(jobName)
            .orElseThrow(() -> new JobNotFoundException(jobName));

        jobDef.setEnabled(true);
        jobDefinitionRepository.save(jobDef);

        // Automatically load enabled jobs
        LoadResult result = jobLoaderService.loadJobFromJar(jobDef.getId());

        return ResponseEntity.ok(StatusResponse.from(result));
    }

    @PutMapping("/{jobName}/disable")
    public ResponseEntity<StatusResponse> disableJob(@PathVariable String jobName) {
        JobDefinition jobDef = jobDefinitionRepository.findByJobName(jobName)
            .orElseThrow(() -> new JobNotFoundException(jobName));

        jobDef.setEnabled(false);
        jobDefinitionRepository.save(jobDef);

        jobLoaderService.unloadJob(jobName);

        return ResponseEntity.ok(StatusResponse.success("Job disabled"));
    }

    @PostMapping("/{jobName}/reload")
    public ResponseEntity<StatusResponse> reloadJob(@PathVariable String jobName) {
        LoadResult result = jobLoaderService.reloadJob(jobName);
        return ResponseEntity.ok(StatusResponse.from(result));
    }

    @GetMapping
    public ResponseEntity<List<JobDefinitionDto>> listJobs() {
        List<JobDefinition> jobs = jobDefinitionRepository.findAll();
        return ResponseEntity.ok(
            jobs.stream()
                .map(JobDefinitionDto::from)
                .collect(Collectors.toList())
        );
    }

    @GetMapping("/{jobName}")
    public ResponseEntity<JobDefinitionDto> getJob(@PathVariable String jobName) {
        JobDefinition jobDef = jobDefinitionRepository.findByJobName(jobName)
            .orElseThrow(() -> new JobNotFoundException(jobName));
        return ResponseEntity.ok(JobDefinitionDto.from(jobDef));
    }

    @DeleteMapping("/{jobName}")
    public ResponseEntity<StatusResponse> deleteJob(@PathVariable String jobName) {
        JobDefinition jobDef = jobDefinitionRepository.findByJobName(jobName)
            .orElseThrow(() -> new JobNotFoundException(jobName));

        // Unload if loaded
        jobLoaderService.unloadJob(jobName);

        // Delete JAR file
        try {
            Files.deleteIfExists(Paths.get(jobDef.getJarFilePath()));
        } catch (IOException e) {
            log.warn("Failed to delete JAR file", e);
        }

        // Delete from database
        jobDefinitionRepository.delete(jobDef);

        return ResponseEntity.ok(StatusResponse.success("Job deleted"));
    }
}
```

### 6. Example Job Plugin Project

**Project Structure:**
```
example-payment-job/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/payment/
│   │   │       ├── PaymentJobPlugin.java
│   │   │       ├── PaymentProcessor.java
│   │   │       ├── PaymentReader.java
│   │   │       └── PaymentWriter.java
│   │   └── resources/
│   │       └── META-INF/
│   │           └── services/
│   │               └── com.fronzec.api.BatchJobPlugin
│   └── test/
└── README.md
```

**pom.xml:**
```xml
<dependencies>
    <!-- Job Plugin API -->
    <dependency>
        <groupId>com.fronzec</groupId>
        <artifactId>batch-job-api</artifactId>
        <version>1.0.0</version>
        <scope>provided</scope> <!-- Provided by parent platform -->
    </dependency>

    <!-- Spring Boot dependencies (provided) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
        <scope>provided</scope>
    </dependency>

    <!-- Job-specific dependencies (packaged) -->
    <dependency>
        <groupId>com.some-payment-api</groupId>
        <artifactId>payment-sdk</artifactId>
        <version>2.1.0</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <artifactSet>
                            <includes>
                                <!-- Include only job-specific dependencies -->
                                <include>com.some-payment-api:payment-sdk</include>
                            </includes>
                        </artifactSet>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**PaymentJobPlugin.java:**
```java
package com.example.payment;

import com.fronzec.api.BatchJobPlugin;
import com.fronzec.api.JobMetadata;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class PaymentJobPlugin implements BatchJobPlugin {

    @Override
    public String getJobName() {
        return "payment-processing";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Job configureJob(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           ApplicationContext parentContext) {

        // Get required beans from parent context
        DataSource dataSource = parentContext.getBean(DataSource.class);

        // Create reader, processor, writer
        PaymentReader reader = new PaymentReader(dataSource);
        PaymentProcessor processor = new PaymentProcessor();
        PaymentWriter writer = new PaymentWriter();

        // Build step
        Step step = new StepBuilder("paymentProcessingStep", jobRepository)
            .<Payment, ProcessedPayment>chunk(100, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();

        // Build job
        return new JobBuilder(getJobName(), jobRepository)
            .start(step)
            .build();
    }

    @Override
    public Map<String, String> getDefaultParameters() {
        return Map.of(
            "DATE", "2024-01-01",
            "BATCH_SIZE", "1000"
        );
    }

    @Override
    public List<String> getRequiredDependencies() {
        return List.of("dataSource", "transactionManager");
    }

    @Override
    public JobMetadata getMetadata() {
        return new JobMetadata() {
            @Override
            public String getDisplayName() {
                return "Payment Processing Job";
            }

            @Override
            public String getDescription() {
                return "Processes daily payment transactions and generates reports";
            }

            @Override
            public String getAuthor() {
                return "Payment Team";
            }

            @Override
            public List<String> getTags() {
                return List.of("payment", "financial", "daily");
            }

            @Override
            public Duration getEstimatedRuntime() {
                return Duration.ofMinutes(30);
            }
        };
    }
}
```

**META-INF/services/com.fronzec.api.BatchJobPlugin:**
```
com.example.payment.PaymentJobPlugin
```

### 7. Integration with Existing JobsManagerService

**Refactor approach:**
```java
@Service
public class JobsManagerService {

    private final JobDefinitionRepository jobDefinitionRepository;
    private final DynamicJobLoaderService jobLoaderService;
    // ... existing fields

    @PostConstruct
    public void init() {
        // Load enabled jobs from database
        List<JobDefinition> enabledJobs = jobDefinitionRepository.findByEnabled(true);

        for (JobDefinition jobDef : enabledJobs) {
            try {
                jobLoaderService.loadJobFromJar(jobDef.getId());
                logger.info("Auto-loaded job: {}", jobDef.getJobName());
            } catch (Exception e) {
                logger.error("Failed to auto-load job: {}", jobDef.getJobName(), e);
            }
        }

        // Keep existing static job registration for backward compatibility
        // ... existing init code
    }

    // Existing methods remain largely unchanged
    // They will work with both static and dynamic jobs
}
```

## Consequences

### Positive

1. **Agility**: Teams can develop and deploy jobs independently
2. **Isolation**: Job failures don't affect other jobs or core platform
3. **Versioning**: Multiple versions can coexist, easy rollback
4. **Scalability**: Core platform doesn't grow with every new job
5. **Flexibility**: Enable/disable jobs without deployment
6. **Testability**: Jobs can be tested in isolation
7. **Reusability**: Jobs can be shared across environments
8. **Auditability**: Complete history of job deployments in database

### Negative

1. **Complexity**: Significant increase in system complexity
2. **Memory Management**: Risk of memory leaks from classloaders
3. **Debugging Difficulty**: Harder to debug across classloader boundaries
4. **Transaction Boundaries**: Care needed with transaction management across classloaders
5. **Classpath Conflicts**: Potential for subtle bugs due to library version conflicts
6. **Performance Overhead**: Classloader delegation adds minor overhead
7. **Operational Burden**: Need to manage JAR storage, versioning, cleanup
8. **Security Risks**: Uploaded JARs could contain malicious code

### Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Memory leaks from classloaders | High | High | Implement proper cleanup, monitoring, automated tests |
| Malicious JAR uploads | Medium | Critical | JAR signing, checksum validation, sandboxing, approval workflow |
| Version conflicts | Medium | Medium | Isolated classloaders, dependency documentation |
| Performance degradation | Low | Medium | Benchmarking, lazy loading, caching |
| Job incompatibility after platform upgrade | Medium | High | Versioned API, compatibility testing, deprecation policy |
| Database corruption | Low | Critical | Regular backups, transaction management, validation |
| Storage exhaustion | Medium | Medium | Quota limits, cleanup policies, monitoring |

### Trade-offs

**Chosen Approach vs. Alternatives:**

| Aspect | Dynamic Loading | Microservices | Script-Based |
|--------|----------------|---------------|--------------|
| Complexity | Medium | High | Low |
| Isolation | Medium | High | Low |
| Performance | Good | Medium (network) | Good |
| Resource Usage | Medium | High | Low |
| Development Effort | High | Very High | Low |
| Operational Overhead | Medium | High | Low |
| Security | Medium | High | Low |
| Spring Integration | Excellent | Medium | Medium |

## Alternatives Considered

### Alternative 1: Microservices Architecture

**Description:** Each job as a separate Spring Boot microservice with REST API

**Pros:**
- Complete isolation (separate processes)
- Independent scaling
- Technology heterogeneity
- Standard deployment patterns

**Cons:**
- Much higher resource usage (JVM per job)
- Network latency for orchestration
- Complex service discovery and coordination
- Higher operational overhead
- Overkill for batch processing

**Verdict:** Rejected - Too heavyweight for batch jobs that run infrequently

### Alternative 2: Script-Based Jobs (Groovy/Kotlin Scripts)

**Description:** Store job logic as scripts in database, execute via scripting engine

**Pros:**
- Maximum flexibility
- No JAR packaging needed
- Easy to modify jobs
- Low complexity

**Cons:**
- Security nightmare (arbitrary code execution)
- Poor performance compared to compiled code
- Difficult to test and debug
- Limited IDE support
- No compile-time safety

**Verdict:** Rejected - Security and maintainability concerns

### Alternative 3: GraalVM Native Images

**Description:** Compile each job to native binary, execute as separate process

**Pros:**
- Best isolation
- Fast startup
- Low memory footprint
- No classloader issues

**Cons:**
- Limited Spring Batch support in native mode
- Long compilation times
- Reflection configuration complexity
- Limited runtime flexibility
- Difficult debugging

**Verdict:** Rejected - Not mature enough for Spring Batch ecosystem

### Alternative 4: Keep Current Approach

**Description:** Continue with monolithic jobs in single codebase

**Pros:**
- Simple to understand
- No additional complexity
- Easy debugging
- Good IDE support

**Cons:**
- Cannot add jobs without redeployment
- Tight coupling
- No version management
- Difficult to scale development teams

**Verdict:** Rejected - Does not meet business requirements for agility

## Implementation Plan

### Phase 1: Foundation (Week 1-2)
- [x] Create `batch-job-api` module with plugin interfaces
- [x] Design and implement database schema (Flyway migrations)
- [x] Set up artifact repository for API module
- [x] Create documentation for job plugin development

**Deliverables:**
- `batch-job-api` JAR published to Maven repository
- Database migrations applied
- Developer guide for creating job plugins

### Phase 2: Core Infrastructure (Week 3-4)
- [ ] Implement `DynamicJobClassLoader`
- [ ] Implement `ClassLoaderManager`
- [ ] Implement `DynamicJobLoaderService`
- [ ] Create JPA entities and repositories for job registry
- [ ] Add comprehensive unit tests

**Deliverables:**
- Core loading mechanism functional
- Unit test coverage >80%
- Memory leak tests passing

### Phase 3: API Layer (Week 5)
- [ ] Implement `JobManagementController`
- [ ] Add file upload handling
- [ ] Implement job CRUD operations
- [ ] Add validation and error handling
- [ ] Create integration tests

**Deliverables:**
- REST API fully functional
- Postman collection for testing
- Integration tests passing

### Phase 4: Integration (Week 6)
- [ ] Refactor `JobsManagerService` to support dynamic jobs
- [ ] Ensure backward compatibility with static jobs
- [ ] Add health checks for loaded jobs
- [ ] Implement metrics and monitoring
- [ ] Performance testing

**Deliverables:**
- Seamless integration with existing system
- Backward compatibility verified
- Performance benchmarks documented

### Phase 5: Security and Hardening (Week 7)
- [ ] Implement JAR signature verification
- [ ] Add checksum validation
- [ ] Create job approval workflow (optional)
- [ ] Add audit logging
- [ ] Security testing

**Deliverables:**
- Security controls implemented
- Audit trail functional
- Security review completed

### Phase 6: Example and Documentation (Week 8)
- [ ] Create example job plugin project
- [ ] Write comprehensive documentation
- [ ] Create video tutorials
- [ ] Set up CI/CD pipeline for example job
- [ ] Production readiness checklist

**Deliverables:**
- Working example job
- Complete documentation
- CI/CD templates
- Production deployment guide

### Phase 7: Production Deployment (Week 9-10)
- [ ] Deploy to staging environment
- [ ] Load testing
- [ ] Operational runbook
- [ ] Monitoring dashboards
- [ ] Training for operations team
- [ ] Production deployment

**Deliverables:**
- System live in production
- Operations team trained
- Monitoring in place

## Success Metrics

### Technical Metrics
- [ ] Job load time < 5 seconds
- [ ] Memory overhead per loaded job < 50MB
- [ ] Zero memory leaks after 100 load/unload cycles
- [ ] API response time < 100ms (95th percentile)
- [ ] Support for 50+ concurrent loaded jobs

### Business Metrics
- [ ] Reduce time to deploy new job from days to hours
- [ ] Enable 3+ teams to develop jobs independently
- [ ] 90% of new jobs deployed without platform changes
- [ ] Zero production incidents due to job loading

## Open Questions

1. **Job Versioning Strategy**: Should we support running multiple versions simultaneously?
   - **Decision Needed**: Define versioning policy (semantic versioning required?)

2. **Job Dependency Management**: How to handle shared job libraries?
   - **Options**: Shared classloader, parent context beans, explicit dependencies

3. **Job Approval Process**: Should all uploaded jobs require approval before enabling?
   - **Options**: Auto-approve for trusted teams, manual approval for all, approval based on environment

4. **Storage Strategy**: Where to store JAR files long-term?
   - **Options**: Local filesystem, S3, NFS, Artifactory

5. **Hot Reload Impact**: Should running job instances be stopped when reloading?
   - **Options**: Graceful drain, force stop, wait for completion

6. **Multi-instance Deployment**: How to handle job loading across multiple service instances?
   - **Options**: Shared storage + database coordination, leader election, manual sync

## References

### Related Documentation
- [Spring Batch Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)
- [Java ClassLoader Documentation](https://docs.oracle.com/javase/8/docs/api/java/lang/ClassLoader.html)
- [Spring Boot DevTools (hot reload reference)](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.devtools)
- [OSGi Dynamic Module System](https://www.osgi.org/developer/architecture/) (inspiration)

### Similar Implementations
- Jenkins Plugin System
- Elasticsearch Plugin Architecture
- Apache Kafka Connect Plugins
- SonarQube Plugin System

### Security References
- [OWASP - Deserialization of untrusted data](https://owasp.org/www-community/vulnerabilities/Deserialization_of_untrusted_data)
- [Java Security Manager (deprecated but concepts relevant)](https://docs.oracle.com/javase/tutorial/essential/environment/security.html)

## Decision Makers

- **Architect**: [Name TBD]
- **Tech Lead**: [Name TBD]
- **Security Lead**: [Name TBD]
- **Product Owner**: [Name TBD]

## Review and Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Architect | TBD | TBD | Pending |
| Security | TBD | TBD | Pending |
| Operations | TBD | TBD | Pending |
| Product | TBD | TBD | Pending |

---

**Last Updated**: 2026-03-02
**Next Review**: After Phase 2 completion
