/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.web.multipart.MultipartFile;

/**
 * Pure static utility for computing SHA-256 checksums of files.
 *
 * <p>Extracted from {@code JarUploadService} so it can be shared between upload-time and load-time
 * integrity verification without introducing a Spring dependency.
 */
public final class ChecksumUtil {

  private ChecksumUtil() {
    // utility class — prevent instantiation
  }

  /**
   * Computes the SHA-256 digest of a {@link File} on disk.
   *
   * @param file the file to hash (must exist and be readable)
   * @return lowercase hex-encoded SHA-256 digest
   * @throws RuntimeException if the file cannot be read or SHA-256 is unavailable
   */
  public static String computeSha256(File file) {
    try (InputStream in = new FileInputStream(file)) {
      return computeSha256(in);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read file for checksum calculation: " + file, e);
    }
  }

  /**
   * Computes the SHA-256 digest of a {@link Path} on disk.
   *
   * @param path the file path to hash (must exist and be readable)
   * @return lowercase hex-encoded SHA-256 digest
   */
  public static String computeSha256(Path path) {
    return computeSha256(path.toFile());
  }

  /**
   * Computes the SHA-256 digest of a {@link MultipartFile} (in-memory or temp upload).
   *
   * @param file the uploaded file
   * @return lowercase hex-encoded SHA-256 digest
   * @throws RuntimeException if the file cannot be read or SHA-256 is unavailable
   */
  public static String computeSha256(MultipartFile file) {
    try (InputStream in = file.getInputStream()) {
      return computeSha256(in);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read multipart file for checksum calculation", e);
    }
  }

  // ── internal ──────────────────────────────────────────────────────────────

  /** Reads the entire stream and returns its SHA-256 digest. */
  private static String computeSha256(InputStream in) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buf = new byte[8192];
      int bytesRead;
      while ((bytesRead = in.read(buf)) != -1) {
        digest.update(buf, 0, bytesRead);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read stream for checksum calculation", e);
    }
  }
}
