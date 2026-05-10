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

## Publishing (prepared, not enabled by default)

To publish this artifact to a remote Maven repository (e.g. GitHub Packages, Nexus, Artifactory), you will need:

- A `<distributionManagement>` section in `pom.xml`
- Matching credentials in your `~/.m2/settings.xml`

Example snippet (enable later as needed):

```xml
<distributionManagement>
  <repository>
    <id>company-releases</id>
    <url>https://maven.example.com/releases</url>
  </repository>
  <snapshotRepository>
    <id>company-snapshots</id>
    <url>https://maven.example.com/snapshots</url>
  </snapshotRepository>
</distributionManagement>
```

Then you can publish with:

```bash
mvn -f batch-job-api/pom.xml deploy
```
