/* 2026 */
package com.fronzec.frbatchservice.batchjobs.job2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchStatus;
import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntity;
import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntityRepository;
import com.fronzec.frbatchservice.batchjobs.job1.step3.PayloadItemInfo;
import com.fronzec.frbatchservice.restclients.ApiClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

/** Unit tests for {@link Job2Writer} covering success, failure, and idempotency paths. */
@ExtendWith(MockitoExtension.class)
class Job2WriterTest {

  @Mock private ApiClient apiClient;

  @Mock private DispatchedGroupEntityRepository dispatchedGroupEntityRepository;

  @InjectMocks private Job2Writer writer;

  @Test
  void write_successPath_flipsStatusToSent() {
    DispatchedGroupEntity group = new DispatchedGroupEntity();
    group.setId(1L);
    group.setDispatchStatus(DispatchStatus.ERROR);

    PayloadItemInfo item = new PayloadItemInfo("uuid-x", "Bob", "Jones", "bob@example.com", "Dev");
    RecoveryGroupPayload payload = new RecoveryGroupPayload(group, List.of(item));

    when(apiClient.sendBatch(any())).thenReturn(true);

    Chunk<RecoveryGroupPayload> chunk = new Chunk<>(List.of(payload));
    writer.write(chunk);

    assertEquals(DispatchStatus.SENT, group.getDispatchStatus());
    assertEquals(1, group.getRecordsIncluded());

    ArgumentCaptor<DispatchedGroupEntity> captor =
        ArgumentCaptor.forClass(DispatchedGroupEntity.class);
    verify(dispatchedGroupEntityRepository).save(captor.capture());
    assertEquals(DispatchStatus.SENT, captor.getValue().getDispatchStatus());
  }

  @Test
  void write_failurePath_throwsAndStatusStaysError() {
    DispatchedGroupEntity group = new DispatchedGroupEntity();
    group.setId(2L);
    group.setDispatchStatus(DispatchStatus.ERROR);

    RecoveryGroupPayload payload =
        new RecoveryGroupPayload(group, List.of(new PayloadItemInfo("uuid-y", "C", "D", "c@d.com", "QA")));

    when(apiClient.sendBatch(any())).thenReturn(false);

    Chunk<RecoveryGroupPayload> chunk = new Chunk<>(List.of(payload));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> writer.write(chunk));
    assertEquals("API rejected batch for dispatchedGroupId=2", ex.getMessage());

    assertEquals(DispatchStatus.ERROR, group.getDispatchStatus());
    verify(dispatchedGroupEntityRepository, never()).save(any());
  }

  @Test
  void write_idempotencyGuard_skipsAlreadySent() {
    DispatchedGroupEntity group = new DispatchedGroupEntity();
    group.setId(3L);
    group.setDispatchStatus(DispatchStatus.SENT);

    RecoveryGroupPayload payload =
        new RecoveryGroupPayload(group, List.of(new PayloadItemInfo("uuid-z", "E", "F", "e@f.com", "PM")));

    Chunk<RecoveryGroupPayload> chunk = new Chunk<>(List.of(payload));
    writer.write(chunk);

    // No API call, no status change, no save
    verify(apiClient, never()).sendBatch(any());
    verify(dispatchedGroupEntityRepository, never()).save(any());
    assertEquals(DispatchStatus.SENT, group.getDispatchStatus());
  }
}
