/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies JAR file signatures using the JDK built-in {@link java.util.jar.JarFile}
 * API — no external crypto libraries required.
 *
 * <h3>Modes</h3>
 * <table>
 *   <caption>Behaviour per {@code app.plugins.signature.mode}</caption>
 *   <tr><th>Mode</th><th>Unsigned JAR</th><th>Invalid signature</th><th>Valid signature</th></tr>
 *   <tr><td>{@code permissive} (default)</td><td>{@code valid=true} + WARN</td><td>{@code valid=false} + WARN</td><td>{@code valid=true}</td></tr>
 *   <tr><td>{@code strict}</td><td>{@code valid=false}</td><td>{@code valid=false}</td><td>{@code valid=true}</td></tr>
 * </table>
 *
 * <p>In permissive mode, unsigned JARs are accepted so that local development and
 * quick iteration are not blocked. Production environments should use strict mode.
 *
 * @see SignatureResult
 */
@Component
public class JarSignatureVerifier {

  private static final Logger log = LoggerFactory.getLogger(JarSignatureVerifier.class);

  private static final String META_INF = "META-INF/";

  private final boolean strict;

  /**
   * @param mode signature verification mode — {@code permissive} (default) or {@code strict}
   */
  public JarSignatureVerifier(
      @Value("${app.plugins.signature.mode:permissive}") String mode) {
    this.strict = "strict".equalsIgnoreCase(mode);
    log.info("JAR signature verification mode: {}", mode);
  }

  /**
   * Verifies the signature of a JAR at the given path.
   *
   * @param jarPath path to the JAR file (must exist and be readable)
   * @return verification result with validity, signer information, and any warnings
   * @throws InvalidJarException if the file cannot be opened as a JAR
   */
  public SignatureResult verify(Path jarPath) {
    List<String> warnings = new ArrayList<>();

    try (JarFile jar = new JarFile(jarPath.toFile(), true)) {

      // ── 1. Detect signature-related entries ────────────────────────────────
      boolean hasSignatureFiles = false;
      Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName().toUpperCase();
        if (name.startsWith("META-INF/")) {
          if (name.endsWith(".SF")) {
            hasSignatureFiles = true;
          }
        }
      }

      // ── 2. Unsigned JAR branch ─────────────────────────────────────────────
      if (!hasSignatureFiles) {
        String msg = "JAR is not signed — no signature files found in META-INF";
        warnings.add(msg);
        if (strict) {
          log.warn("{} (strict mode — rejecting)", msg);
          return new SignatureResult(false, null, warnings);
        }
        log.warn("{} (permissive mode — allowing upload)", msg);
        return new SignatureResult(true, null, warnings);
      }

      // ── 3. Signed JAR — verify every entry ─────────────────────────────────
      // Opening JarFile with verify=true means getInputStream() will throw
      // SecurityException on any entry whose digest or signature is invalid.
      String signer = null;
      entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();

        if (entry.isDirectory() || name.startsWith(META_INF)) {
          continue;
        }

        // Extract signer from the first signed entry we encounter
        if (signer == null && entry.getCertificates() != null && entry.getCertificates().length > 0) {
          signer = extractCommonName((X509Certificate) entry.getCertificates()[0]);
        }

        try (InputStream is = jar.getInputStream(entry)) {
          // Force a full read so that JarVerifier has a chance to validate
          byte[] buf = new byte[8192];
          while (is.read(buf) != -1) {
            // drain the stream
          }
        } catch (SecurityException e) {
          String detail = "Entry '" + name + "' failed signature verification: " + e.getMessage();
          warnings.add(detail);
          log.warn(detail);
          return new SignatureResult(false, signer, warnings);
        }
      }

      log.info("JAR signature verified successfully. Signer: {}", signer != null ? signer : "<unknown>");
      return new SignatureResult(true, signer, warnings);

    } catch (IOException e) {
      throw new InvalidJarException("Failed to open JAR for signature verification: " + e.getMessage());
    }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /** Extracts the CN (common name) from an X.509 subject DN, or falls back to the full DN. */
  private static String extractCommonName(X509Certificate cert) {
    String dn = cert.getSubjectX500Principal().getName();
    for (String part : dn.split(",")) {
      part = part.trim();
      if (part.toUpperCase().startsWith("CN=")) {
        return part.substring(3);
      }
    }
    return dn;
  }
}
