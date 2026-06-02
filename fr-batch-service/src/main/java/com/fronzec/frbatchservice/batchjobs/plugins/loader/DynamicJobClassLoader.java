/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs.plugins.loader;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

/**
 * A parent-last {@link URLClassLoader} that isolates plugin classes while sharing core Spring
 * infrastructure through shared-package delegation.
 *
 * <p>Shared packages ({@code org.springframework.}, {@code jakarta.}, {@code org.slf4j.},
 * {@code com.fronzec.api.}) are always delegated to the parent classloader. All other classes are
 * loaded from the provided JAR URLs first, falling back to the parent only if not found in the
 * JAR.
 *
 * <p><strong>Thread safety:</strong> {@code loadClass} uses per-class-name locking via
 * {@link #getClassLoadingLock(String)}, matching the standard classloader contract.
 */
public class DynamicJobClassLoader extends URLClassLoader {

  private static final Set<String> SHARED_PACKAGES =
      Set.of(
          "org.springframework.",
          "jakarta.",
          "org.slf4j.",
          "com.fronzec.api.");

  private final String jobName;

  /**
   * @param urls JAR URLs to load plugin classes from
   * @param parent the parent classloader (typically the application classloader, providing shared
   *     infrastructure)
   * @param jobName human-readable identifier for diagnostics
   */
  public DynamicJobClassLoader(URL[] urls, ClassLoader parent, String jobName) {
    super(urls, parent);
    this.jobName = jobName;
  }

  /**
   * Parent-last with shared-package filter.
   *
   * <p>Classes in shared packages are delegated to the parent immediately. All other classes are
   * searched in this classloader's JAR URLs first, with the parent as a fallback.
   */
  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    // Shared packages — delegate to parent without consulting JAR
    if (SHARED_PACKAGES.stream().anyMatch(name::startsWith)) {
      return getParent().loadClass(name);
    }

    // Parent-last: try this classloader first, fall back to parent
    synchronized (getClassLoadingLock(name)) {
      Class<?> c = findLoadedClass(name);
      if (c == null) {
        try {
          c = findClass(name);
        } catch (ClassNotFoundException e) {
          c = getParent().loadClass(name);
        }
      }
      if (resolve) {
        resolveClass(c);
      }
      return c;
    }
  }

  /** Best-effort close; safe to call multiple times. */
  public void cleanup() {
    try {
      close();
    } catch (Exception e) {
      // best-effort
    }
  }

  /** Returns the job name this classloader was created for. */
  public String getJobName() {
    return jobName;
  }
}
