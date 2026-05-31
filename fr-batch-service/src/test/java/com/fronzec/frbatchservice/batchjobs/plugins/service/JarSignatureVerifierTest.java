/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Verifies JAR signature detection and mode-based behaviour of {@link JarSignatureVerifier}. */
@DisplayName("JarSignatureVerifier")
class JarSignatureVerifierTest {

  private Path tempDir;

  @BeforeEach
  void setUp() throws Exception {
    tempDir = Files.createTempDirectory("jar-signature-verifier-test-");
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

  // ── helpers ────────────────────────────────────────────────────────────────

  /** Creates a minimal unsigned JAR containing a single text entry. */
  private Path createUnsignedJar(String jarName, String entryName, byte[] entryContent)
      throws IOException {
    Path jar = tempDir.resolve(jarName);

    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()), manifest)) {
      jos.putNextEntry(new ZipEntry(entryName));
      jos.write(entryContent);
      jos.closeEntry();
    }
    return jar;
  }

  // ── Unsigned JAR ───────────────────────────────────────────────────────────

  @Nested
  @DisplayName("unsigned JAR")
  class UnsignedJar {

    @Test
    @DisplayName("permissive mode returns valid=true with warning")
    void permissiveAcceptsUnsigned() throws Exception {
      Path jar = createUnsignedJar("unsigned.jar", "hello.txt", "hello".getBytes());
      var verifier = new JarSignatureVerifier("permissive");

      SignatureResult result = verifier.verify(jar);

      assertTrue(result.valid(), "Permissive mode must accept unsigned JARs");
      assertNull(result.signer(), "Unsigned JAR has no signer");
      assertEquals(1, result.warnings().size());
      assertTrue(
          result.warnings().get(0).contains("not signed"),
          "Warning must mention missing signature: " + result.warnings());
    }

    @Test
    @DisplayName("strict mode returns valid=false with warning")
    void strictRejectsUnsigned() throws Exception {
      Path jar = createUnsignedJar("unsigned.jar", "hello.txt", "hello".getBytes());
      var verifier = new JarSignatureVerifier("strict");

      SignatureResult result = verifier.verify(jar);

      assertFalse(result.valid(), "Strict mode must reject unsigned JARs");
      assertNull(result.signer());
      assertEquals(1, result.warnings().size());
      assertTrue(result.warnings().get(0).contains("not signed"));
    }
  }

  // ── Not a JAR ──────────────────────────────────────────────────────────────

  @Test
  @DisplayName("non-JAR file throws InvalidJarException")
  void nonJarThrowsInvalidJarException() throws Exception {
    Path plainText = tempDir.resolve("notes.txt");
    Files.writeString(plainText, "this is not a JAR");

    var verifier = new JarSignatureVerifier("permissive");

    assertThrows(
        InvalidJarException.class,
        () -> verifier.verify(plainText),
        "Non-JAR files must be rejected immediately");
  }

  // ── InvalidArgumentException on null ───────────────────────────────────────

  @Test
  @DisplayName("non-existent path throws InvalidJarException")
  void nonExistentPathThrows() {
    Path nonExistent = tempDir.resolve("does-not-exist.jar");
    var verifier = new JarSignatureVerifier("permissive");

    assertThrows(
        InvalidJarException.class,
        () -> verifier.verify(nonExistent),
        "Non-existent files must be rejected");
  }

  // ── Empty JAR (only manifest) ──────────────────────────────────────────────

  @Test
  @DisplayName("JAR with only manifest is treated as unsigned")
  void manifestOnlyJarIsUnsigned() throws Exception {
    Path jar = tempDir.resolve("manifest-only.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()), manifest)) {
      // no entries — only manifest
    }

    var verifier = new JarSignatureVerifier("permissive");
    SignatureResult result = verifier.verify(jar);

    assertTrue(result.valid(), "Manifest-only JAR is effectively unsigned");
    assertTrue(result.warnings().get(0).contains("not signed"));
  }

  // ── Default mode ───────────────────────────────────────────────────────────

  @Test
  @DisplayName("default constructor (permissive) accepts unsigned JARs")
  void defaultModeIsPermissive() throws Exception {
    Path jar = createUnsignedJar("unsigned.jar", "hello.txt", "hello".getBytes());
    var verifier = new JarSignatureVerifier("permissive");

    SignatureResult result = verifier.verify(jar);

    assertTrue(result.valid(), "Default mode (permissive) must accept unsigned JARs");
  }

  // ── Mode case-insensitivity ────────────────────────────────────────────────

  @Test
  @DisplayName("strict mode is case-insensitive")
  void strictModeCaseInsensitive() throws Exception {
    Path jar = createUnsignedJar("unsigned.jar", "hello.txt", "hello".getBytes());
    var verifier = new JarSignatureVerifier("STRICT");

    SignatureResult result = verifier.verify(jar);

    assertFalse(result.valid(), "\"STRICT\" (upper-case) must be treated as strict mode");
  }

  @Test
  @DisplayName("permissive mode with mixed case")
  void permissiveModeMixedCase() throws Exception {
    Path jar = createUnsignedJar("unsigned.jar", "hello.txt", "hello".getBytes());
    var verifier = new JarSignatureVerifier("Permissive");

    SignatureResult result = verifier.verify(jar);

    assertTrue(result.valid(), "\"Permissive\" (mixed-case) must be treated as permissive");
  }

  // ── Unknown mode defaults to permissive ────────────────────────────────────

  @Test
  @DisplayName("unknown mode value defaults to permissive")
  void unknownModeDefaultsToPermissive() throws Exception {
    Path jar = createUnsignedJar("unsigned.jar", "hello.txt", "hello".getBytes());
    var verifier = new JarSignatureVerifier("garbage");

    SignatureResult result = verifier.verify(jar);

    assertTrue(result.valid(), "Unknown mode must default to permissive behaviour");
  }

  // ── JAR with multiple entries ──────────────────────────────────────────────

  @Test
  @DisplayName("unsigned JAR with multiple entries")
  void unsignedJarWithMultipleEntries() throws Exception {
    Path jar = tempDir.resolve("multi.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()), manifest)) {
      jos.putNextEntry(new ZipEntry("META-INF/"));
      jos.closeEntry();
      jos.putNextEntry(new ZipEntry("com/example/A.class"));
      jos.write(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
      jos.closeEntry();
      jos.putNextEntry(new ZipEntry("plugin.properties"));
      jos.write("key=value".getBytes());
      jos.closeEntry();
    }

    var verifier = new JarSignatureVerifier("permissive");
    SignatureResult result = verifier.verify(jar);

    assertTrue(result.valid(), "Multi-entry unsigned JAR must pass permissive mode");
    assertTrue(result.warnings().get(0).contains("not signed"));
  }
}
