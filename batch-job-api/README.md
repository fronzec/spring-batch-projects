# batch-job-api

This module defines the public contract for dynamic Spring Batch job plugins.

## Build

```bash
mvn -f batch-job-api/pom.xml clean package
```

## Install locally (for development)

If you want `fr-batch-service` to resolve this dependency from your local Maven repository:

```bash
mvn -f batch-job-api/pom.xml clean install
```

## Publishing

This artifact is published to **GitHub Packages** at `https://maven.pkg.github.com/fronzec/spring-batch-plugin-lab`.

### 1. GitHub Personal Access Token

Create a token with `write:packages` scope at:
**GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)**

### 2. Configure credentials

Add the following to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>your-github-username</username>
      <password>ghp_xxxxxxxxxxxxxxxxxxxx</password>
    </server>
  </servers>
</settings>
```

### 3. Publish

```bash
mvn -f batch-job-api/pom.xml deploy
```

Releases are immutable on GitHub Packages. Use `-SNAPSHOT` versions during development — they can be overwritten.

### Consuming

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/fronzec/spring-batch-plugin-lab</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.fronzec</groupId>
  <artifactId>batch-job-api</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```
