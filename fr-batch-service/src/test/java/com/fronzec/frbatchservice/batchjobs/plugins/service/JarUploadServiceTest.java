/* 2024-2026 */
package com.fronzec.frbatchservice.batchjobs.plugins.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fronzec.frbatchservice.batchjobs.plugins.audit.AuditService;
import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import com.fronzec.frbatchservice.batchjobs.plugins.metrics.PluginMetrics;
import com.fronzec.frbatchservice.batchjobs.plugins.repository.JobDefinitionRepository;
import com.fronzec.frbatchservice.batchjobs.plugins.util.ChecksumUtil;
import com.fronzec.frbatchservice.config.AutoApproveConfig;
import com.fronzec.frbatchservice.web.dto.JarUploadResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** Verifies the upload pipeline of {@link JarUploadService}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("JarUploadService")
class JarUploadServiceTest {

  @Mock private JobDefinitionRepository jobDefinitionRepository;
  @Mock private JarSignatureVerifier jarSignatureVerifier;
  @Mock private AuditService auditService;
  @Mock private PluginMetrics pluginMetrics;

  @TempDir private Path jarDir;

  private JarUploadService service;

  @BeforeEach
  void setUp() {
    service =
        new JarUploadService(
            jobDefinitionRepository,
            Optional.<AutoApproveConfig>empty(),
            jarSignatureVerifier,
            auditService,
            pluginMetrics,
            "permissive",
            jarDir.toString());
  }

  @Test
  @DisplayName("computes the persisted checksum from the stored file, never from the multipart stream")
  void uploadJar_checksumIsComputedFromStoredFile_notMultipartStream() throws IOException {
    byte[] jarBytes = minimalJar();
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test-job-1.0.0.jar", "application/java-archive", jarBytes);
    Path storedPath = jarDir.resolve("test-job-1.0.0.jar");

    when(jobDefinitionRepository.findByJobName("test-job")).thenReturn(Optional.empty());
    when(jarSignatureVerifier.verify(any()))
        .thenReturn(new SignatureResult(true, null, List.of()));
    when(jobDefinitionRepository.save(any(JobDefinitionEntity.class)))
        .thenAnswer(
            invocation -> {
              JobDefinitionEntity saved = invocation.getArgument(0);
              saved.setId(1L);
              return saved;
            });

    JarUploadResponse response;
    try (MockedStatic<ChecksumUtil> checksum = mockStatic(ChecksumUtil.class)) {
      // Stub only the Path overload: the digest the service stores must come from
      // the persisted file. The multipart overload is left unstubbed so that a
      // regression to hashing the stream would surface a null checksum AND trip
      // the never() verification below.
      checksum.when(() -> ChecksumUtil.computeSha256(storedPath)).thenReturn("disk-digest");

      response = service.uploadJar(file, "test-job", "1.0.0", "com.example.MyPlugin");

      // The checksum was read from the persisted file, not from the upload stream.
      checksum.verify(() -> ChecksumUtil.computeSha256(storedPath));
      checksum.verify(() -> ChecksumUtil.computeSha256(any(MultipartFile.class)), never());
    }

    ArgumentCaptor<JobDefinitionEntity> captor =
        ArgumentCaptor.forClass(JobDefinitionEntity.class);
    verify(jobDefinitionRepository).save(captor.capture());

    assertTrue(Files.exists(storedPath), "JAR should be written to disk before hashing");
    assertArrayEquals(
        jarBytes, Files.readAllBytes(storedPath), "stored bytes should match the upload");
    assertEquals(
        "disk-digest",
        captor.getValue().getJarChecksum(),
        "persisted checksum must be the digest of the stored file");
    assertEquals("disk-digest", response.jarChecksum(), "response must expose the same checksum");
  }

  /** Builds a minimal valid (unsigned) JAR with a manifest and one entry. */
  private static byte[] minimalJar() throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (JarOutputStream jar = new JarOutputStream(out, manifest)) {
      jar.putNextEntry(new ZipEntry("com/example/MyPlugin.class"));
      jar.write("stub".getBytes());
      jar.closeEntry();
    }
    return out.toByteArray();
  }
}
