/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs.plugins.loader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.jar.*;
import javax.tools.*;
import org.junit.jupiter.api.*;

class DynamicJobClassLoaderTest {

  private Path tempDir;
  private Path jar1Path;
  private Path jar2Path;
  private ClassLoader parent;

  @BeforeEach
  void setUp() throws Exception {
    tempDir = Files.createTempDirectory("classloader-test-");
    parent = getClass().getClassLoader();

    // v1: "say" returns "v1"
    jar1Path =
        createTestJar(
            tempDir,
            "com.test.MyPlugin",
            "package com.test;\npublic class MyPlugin { public String say() { return \"v1\"; } }",
            "jar1");

    // v2: same class name, different behavior (isolation test)
    jar2Path =
        createTestJar(
            tempDir,
            "com.test.MyPlugin",
            "package com.test;\npublic class MyPlugin { public String say() { return \"v2\"; } }",
            "jar2");
  }

  @AfterEach
  void tearDown() {
    if (tempDir != null) {
      try (var walk = Files.walk(tempDir)) {
        walk
            .sorted(Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException ignored) {
                    // best-effort cleanup
                  }
                });
      } catch (IOException ignored) {
        // best-effort cleanup
      }
    }
  }

  // ── Shared package delegation ────────────────────────────────────────────

  @Test
  void sharedPackages_delegateSpringToParent() throws Exception {
    DynamicJobClassLoader loader =
        new DynamicJobClassLoader(new URL[] {jar1Path.toUri().toURL()}, parent, "test-job");

    Class<?> loaded = loader.loadClass("org.springframework.batch.core.job.Job");
    assertSame(
        org.springframework.batch.core.job.Job.class,
        loaded,
        "Spring class should come from parent, not JAR");
  }

  @Test
  void sharedPackages_delegateSlf4jToParent() throws Exception {
    DynamicJobClassLoader loader =
        new DynamicJobClassLoader(new URL[] {jar1Path.toUri().toURL()}, parent, "test-job");

    Class<?> loaded = loader.loadClass("org.slf4j.Logger");
    assertSame(
        org.slf4j.Logger.class, loaded, "SLF4J class should come from parent");
  }

  @Test
  void sharedPackages_delegateJakartaToParent() throws Exception {
    DynamicJobClassLoader loader =
        new DynamicJobClassLoader(new URL[] {jar1Path.toUri().toURL()}, parent, "test-job");

    Class<?> loaded = loader.loadClass("jakarta.annotation.PostConstruct");
    assertSame(
        jakarta.annotation.PostConstruct.class,
        loaded,
        "Jakarta class should come from parent");
  }

  @Test
  void sharedPackages_delegateApiToParent() throws Exception {
    DynamicJobClassLoader loader =
        new DynamicJobClassLoader(new URL[] {jar1Path.toUri().toURL()}, parent, "test-job");

    Class<?> loaded = loader.loadClass("com.fronzec.api.BatchJobPlugin");
    assertSame(
        com.fronzec.api.BatchJobPlugin.class,
        loaded,
        "API class should come from parent");
  }

  // ── JAR-first loading ────────────────────────────────────────────────────

  @Test
  void jarClass_loadedFromJarFirst() throws Exception {
    DynamicJobClassLoader loader =
        new DynamicJobClassLoader(new URL[] {jar1Path.toUri().toURL()}, parent, "test-job");

    Class<?> pluginClass = loader.loadClass("com.test.MyPlugin");
    assertNotNull(pluginClass);
    assertEquals("com.test.MyPlugin", pluginClass.getName());

    // Verify isolation: the class was loaded by our loader, not the parent
    assertSame(
        loader,
        pluginClass.getClassLoader(),
        "Plugin class should be loaded by DynamicJobClassLoader");
  }

  @Test
  void classloaderIsolation_sameClassNameDifferentJars_yieldDifferentClasses() throws Exception {
    DynamicJobClassLoader loader1 =
        new DynamicJobClassLoader(new URL[] {jar1Path.toUri().toURL()}, parent, "job1");
    DynamicJobClassLoader loader2 =
        new DynamicJobClassLoader(new URL[] {jar2Path.toUri().toURL()}, parent, "job2");

    Class<?> class1 = loader1.loadClass("com.test.MyPlugin");
    Class<?> class2 = loader2.loadClass("com.test.MyPlugin");

    assertNotNull(class1);
    assertNotNull(class2);
    assertNotSame(
        class1,
        class2,
        "Same class name from different JARs should yield different Class objects");

    // Verify different behavior from each class
    Object instance1 = class1.getDeclaredConstructor().newInstance();
    Object instance2 = class2.getDeclaredConstructor().newInstance();

    assertEquals("v1", class1.getMethod("say").invoke(instance1));
    assertEquals("v2", class2.getMethod("say").invoke(instance2));
  }

  // ── Parent fallback ──────────────────────────────────────────────────────

  @Test
  void nonSharedClass_notInJar_fallsBackToParent() throws Exception {
    DynamicJobClassLoader loader =
        new DynamicJobClassLoader(new URL[] {jar1Path.toUri().toURL()}, parent, "test-job");

    // java.util.ArrayList is not in shared packages, not in JAR → parent fallback
    Class<?> listClass = loader.loadClass("java.util.ArrayList");
    assertSame(
        java.util.ArrayList.class,
        listClass,
        "JDK class should fall back to parent after JAR miss");
  }

  @Test
  void unknownClass_notInJar_notInParent_throwsClassNotFoundException() throws Exception {
    DynamicJobClassLoader loader =
        new DynamicJobClassLoader(new URL[] {jar1Path.toUri().toURL()}, parent, "test-job");

    assertThrows(
        ClassNotFoundException.class,
        () -> loader.loadClass("com.nonexistent.Ghost"),
        "Missing class should throw ClassNotFoundException");
  }

  // ── Cleanup ──────────────────────────────────────────────────────────────

  @Test
  void cleanup_closesClassLoader() throws Exception {
    DynamicJobClassLoader loader =
        new DynamicJobClassLoader(new URL[] {jar1Path.toUri().toURL()}, parent, "test-job");

    loader.cleanup();

    // After close(), loading a JAR-only class should fail
    assertThrows(
        Exception.class,
        () -> loader.loadClass("com.test.MyPlugin"),
        "After cleanup, loading JAR-only class should fail");
  }

  @Test
  void cleanup_isIdempotent() throws Exception {
    DynamicJobClassLoader loader =
        new DynamicJobClassLoader(new URL[] {jar1Path.toUri().toURL()}, parent, "test-job");

    loader.cleanup();
    // Second cleanup should not throw
    assertDoesNotThrow(loader::cleanup, "cleanup() should be idempotent");
  }

  // ── Metadata ─────────────────────────────────────────────────────────────

  @Test
  void getJobName_returnsConstructorArgument() {
    DynamicJobClassLoader loader =
        new DynamicJobClassLoader(new URL[] {}, parent, "my-custom-job");
    assertEquals("my-custom-job", loader.getJobName());
  }

  // ── Test JAR helper ──────────────────────────────────────────────────────

  /**
   * Compiles a single Java source file and packages it into a JAR.
   *
   * @param tempDir scratch directory (caller owns cleanup)
   * @param className fully-qualified class name (e.g. "com.test.MyPlugin")
   * @param source complete Java source code
   * @param suffix unique suffix to avoid directory collisions within same tempDir
   * @return path to the generated JAR file
   */
  private static Path createTestJar(
      Path tempDir, String className, String source, String suffix) throws Exception {

    int lastDot = className.lastIndexOf('.');
    String packageName = lastDot >= 0 ? className.substring(0, lastDot) : "";
    String simpleName = lastDot >= 0 ? className.substring(lastDot + 1) : className;

    // Write source file into tempDir/src-{suffix}/...
    Path srcDir = tempDir.resolve("src-" + suffix);
    Path packageDir = srcDir;
    if (!packageName.isEmpty()) {
      for (String part : packageName.split("\\.")) {
        packageDir = packageDir.resolve(part);
      }
    }
    Files.createDirectories(packageDir);
    Files.writeString(packageDir.resolve(simpleName + ".java"), source);

    // Compile to tempDir/classes-{suffix}
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Path classesDir = tempDir.resolve("classes-" + suffix);
    Files.createDirectories(classesDir);

    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, null, null)) {
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjects(packageDir.resolve(simpleName + ".java"));
      compiler
          .getTask(null, fileManager, null, List.of("-d", classesDir.toString()), null, compilationUnits)
          .call();
    }

    // Package compiled class into a JAR
    Path jarPath = tempDir.resolve(simpleName + "-" + suffix + ".jar");
    try (JarOutputStream jos =
        new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarPath.toFile())))) {
      try (var walk = Files.walk(classesDir)) {
        walk
            .filter(p -> p.toString().endsWith(".class"))
            .forEach(
                p -> {
                  try {
                    String entryName =
                        classesDir.relativize(p).toString().replace(File.separatorChar, '/');
                    jos.putNextEntry(new JarEntry(entryName));
                    Files.copy(p, jos);
                    jos.closeEntry();
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                });
      }
    }

    return jarPath;
  }
}
