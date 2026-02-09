# Agent Guidelines for fr-batch-service

This document outlines the essential commands and coding standards for AI agents operating within the `fr-batch-service` repository. Adhering to these guidelines ensures consistency, maintainability, and proper integration with the existing codebase.

## 1. Build, Lint, and Test Commands

This project uses Maven for dependency management and build automation.

### Build Commands

*   **Clean and Install:** Compiles the project, runs tests, and packages the application into a `.jar` file, installing it into the local Maven repository.
    ```bash
    mvn clean install
    ```
*   **Package Only:** Compiles the project, runs tests, and packages the application without installing it into the local Maven repository.
    ```bash
    mvn package
    ```

### Test Commands

*   **Run All Tests:** Executes all unit and integration tests in the project.
    ```bash
    mvn test
    ```
*   **Run a Single Test Class:** To run tests within a specific Java test class:
    ```bash
    mvn test -Dtest=com.fronzec.batch.MyTestClass
    ```
    (Replace `com.fronzec.batch.MyTestClass` with the fully qualified name of the test class.)

*   **Run a Single Test Method:** To execute a specific test method within a test class:
    ```bash
    mvn test -Dtest=com.fronzec.batch.MyTestClass#myTestMethod
    ```
    (Replace `myTestMethod` with the name of the test method.)

### Linting and Formatting

The project uses `spotless-maven-plugin` for code formatting, although it is currently temporarily disabled. When enabled, it enforces `googleJavaFormat` with AOSP style.

*   **Check Formatting (if enabled):** Verifies code formatting without applying changes.
    ```bash
    mvn spotless:check
    ```
*   **Apply Formatting (if enabled):** Automatically formats the code according to the configured rules.
    ```bash
    mvn spotless:apply
    ```

**Note:** If `spotless-maven-plugin` is re-enabled, ensure to run `mvn spotless:apply` to format your code before committing.

## 2. Code Style Guidelines

The `fr-batch-service` project follows a consistent code style primarily enforced by `googleJavaFormat` (AOSP style) via Spotless.

### General Conventions

*   **Language Version:** Java 21.
*   **Line Length:** Aim for a maximum line length of 140 characters, although Google Java Format (AOSP) will handle most of this automatically.
*   **Indentation:** 2 spaces (configured by Google Java Format and indicated by commented Prettier plugin). Do not use tabs for Java code.
*   **Braces:** Use K&R style for braces (opening brace on the same line as the declaration, closing brace on its own line). This is the default for Google Java Format.

### Imports

*   **Ordering:** Imports are organized as `java|javax,org,com,com.diffplug,,\#com.diffplug,\#`. Static imports are grouped separately after regular imports.
*   **Wildcards:** Wildcard imports (`import com.example.*`) are generally discouraged; prefer explicit imports. If used, they will appear *after* specific imports as `wildcardsLast` is `false`.
*   **Unused Imports:** Unused imports should be removed. Spotless has `removeUnusedImports` configured.

### Naming Conventions

*   **Classes and Interfaces:** PascalCase (e.g., `MyService`, `BatchJobConfiguration`).
*   **Methods:** camelCase (e.g., `processItem`, `findUserById`).
*   **Variables:** camelCase (e.g., `userName`, `batchSize`).
*   **Constants (static final):** SCREAMING_SNAKE_CASE (e.g., `DEFAULT_PAGE_SIZE`, `MAX_RETRIES`).
*   **Packages:** lowercase.

### Types

*   **Generics:** Use generics to ensure type safety (e.g., `List<String>`, `Map<Long, User>`).
*   **Primitive vs. Wrapper Types:** Use primitive types (`int`, `long`, `boolean`) where nullability is not required and performance is critical. Use wrapper types (`Integer`, `Long`, `Boolean`) in collections or when null values are possible.

### Error Handling

*   **Exceptions:**
    *   Use checked exceptions for recoverable errors that the caller is expected to handle (e.g., `IOException`).
    *   Use unchecked exceptions (runtime exceptions) for unrecoverable programming errors or unexpected conditions (e.g., `NullPointerException`, `IllegalArgumentException`).
*   **Logging:** Use the project's established logging framework (e.g., SLF4J with Logback/Log4j) for reporting errors and debugging information. Avoid printing stack traces directly to `System.err`.
*   **Specific Exceptions:** Catch specific exceptions rather than broad `Exception` catches.
*   **Custom Exceptions:** Create custom exception classes when the standard Java exceptions do not adequately describe the error condition.

### Comments

*   **Javadocs:** Provide Javadoc comments for all public classes, interfaces, methods, and significant fields, explaining their purpose, parameters, and return values.
*   **Inline Comments:** Use inline comments sparingly to explain complex logic or non-obvious code sections. Do not comment on self-explanatory code.

### General Best Practices

*   **Immutability:** Favor immutability where possible, especially for data transfer objects and configuration classes.
*   **Dependency Injection:** Utilize Spring's dependency injection for managing components. Avoid manual instantiation of beans.
*   **Single Responsibility Principle:** Classes and methods should ideally have a single, well-defined responsibility.
*   **Avoid Magic Numbers/Strings:** Use named constants or enums instead of hardcoded literal values.
*   **Stream API:** Prefer Java Stream API for collection processing where it improves readability and conciseness.
*   **Optional:** Use `Optional` to clearly indicate that a value might be absent, avoiding `NullPointerExceptions`.

---
