/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies SHA-256 computation consistency for {@link ChecksumUtil}. */
class ChecksumUtilTest {

  private Path tempDir;
  private Path sampleFile;

  @BeforeEach
  void setUp() throws Exception {
    tempDir = Files.createTempDirectory("checksum-util-test-");
    sampleFile = tempDir.resolve("sample.txt");
    Files.writeString(sampleFile, "Hello, Spring Batch plugins!", StandardCharsets.UTF_8);
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
                    // best-effort
                  }
                });
      } catch (IOException ignored) {
        // best-effort
      }
    }
  }

  // ── File overload ─────────────────────────────────────────────────────────

  @Test
  void computeSha256_file_sameContentsSameHash() {
    String hash1 = ChecksumUtil.computeSha256(sampleFile.toFile());
    String hash2 = ChecksumUtil.computeSha256(sampleFile.toFile());

    assertEquals(hash1, hash2, "Same file must produce identical hash");
    assertEquals(64, hash1.length(), "SHA-256 hex digest must be 64 chars");
  }

  @Test
  void computeSha256_file_differentContentsDifferentHash() throws Exception {
    String hash1 = ChecksumUtil.computeSha256(sampleFile.toFile());

    // Modify the file
    Files.writeString(sampleFile, "Modified content!", StandardCharsets.UTF_8);
    String hash2 = ChecksumUtil.computeSha256(sampleFile.toFile());

    assertNotEquals(hash1, hash2, "Modified file must produce different hash");
  }

  @Test
  void computeSha256_file_emptyFile() throws Exception {
    Path emptyFile = tempDir.resolve("empty.dat");
    Files.createFile(emptyFile);

    String hash = ChecksumUtil.computeSha256(emptyFile.toFile());

    assertNotNull(hash);
    assertEquals(64, hash.length(), "Empty file still produces valid hash");
  }

  // ── Path overload ─────────────────────────────────────────────────────────

  @Test
  void computeSha256_path_sameAsFileOverload() {
    String hashByFile = ChecksumUtil.computeSha256(sampleFile.toFile());
    String hashByPath = ChecksumUtil.computeSha256(sampleFile);

    assertEquals(hashByFile, hashByPath, "File and Path overloads must agree");
  }

  @Test
  void computeSha256_path_consistent() {
    String hash1 = ChecksumUtil.computeSha256(sampleFile);
    String hash2 = ChecksumUtil.computeSha256(sampleFile);

    assertEquals(hash1, hash2);
  }

  // ── MultipartFile overload ────────────────────────────────────────────────

  @Test
  void computeSha256_multipartFile_consistent() throws Exception {
    // Simulate a MultipartFile with known content
    byte[] content = "test-plugin-content".getBytes(StandardCharsets.UTF_8);
    var file =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test.jar", "application/java-archive", content);

    String hash1 = ChecksumUtil.computeSha256(file);
    String hash2 = ChecksumUtil.computeSha256(file);

    assertEquals(hash1, hash2, "Same MultipartFile must produce identical hash");
    assertEquals(64, hash1.length());
  }

  @Test
  void computeSha256_multipartFile_matchesFileOverload() throws Exception {
    byte[] content = "match-test".getBytes(StandardCharsets.UTF_8);
    var mf =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "test.jar", "application/java-archive", content);

    Path diskFile = tempDir.resolve("match-test.dat");
    Files.write(diskFile, content);

    String hashByMultipart = ChecksumUtil.computeSha256(mf);
    String hashByFile = ChecksumUtil.computeSha256(diskFile);

    assertEquals(hashByMultipart, hashByFile,
        "MultipartFile and File hashes must match for same content");
  }
}
