/* 2026 */
package com.fronzec.frbatchservice.batchjobs.job2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchStatus;
import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntity;
import com.fronzec.frbatchservice.personv2.PersonV2Repository;
import com.fronzec.frbatchservice.personv2.PersonsV2Entity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link Job2Processor} focusing on the {@code PersonsV2Entity → PayloadItemInfo}
 * transformation. */
@ExtendWith(MockitoExtension.class)
class Job2ProcessorTest {

  @Mock private PersonV2Repository personV2Repository;

  @InjectMocks private Job2Processor processor;

  @Test
  void process_mapsPersonsToPayloadItemsAndWrapsGroup() {
    DispatchedGroupEntity group = new DispatchedGroupEntity();
    group.setId(1L);
    group.setDispatchStatus(DispatchStatus.ERROR);

    PersonsV2Entity person = new PersonsV2Entity();
    person.setId(10L);
    person.setUuidV4("uuid-aaa");
    person.setFirstName("Alice");
    person.setLastName("Smith");
    person.setEmail("alice@example.com");
    person.setProfession("Engineer");
    person.setFkDispatchedGroupId(1L);
    person.setSalary(BigDecimal.valueOf(75000));
    person.setSnapshotDate(LocalDate.of(2026, 1, 1));

    when(personV2Repository.findByFkDispatchedGroupId(1L)).thenReturn(List.of(person));

    RecoveryGroupPayload result = processor.process(group);

    assertNotNull(result);
    assertEquals(group, result.dispatchedGroup());
    assertEquals(1, result.payloadItems().size());

    var item = result.payloadItems().get(0);
    assertEquals("uuid-aaa", item.getUuidV4());
    assertEquals("Alice", item.getFirstName());
    assertEquals("Smith", item.getLastName());
    assertEquals("alice@example.com", item.getEmail());
    assertEquals("Engineer", item.getProfession());
  }

  @Test
  void process_emptyPersonList_producesEmptyPayload() {
    DispatchedGroupEntity group = new DispatchedGroupEntity();
    group.setId(2L);
    group.setDispatchStatus(DispatchStatus.ERROR);

    when(personV2Repository.findByFkDispatchedGroupId(2L)).thenReturn(List.of());

    RecoveryGroupPayload result = processor.process(group);

    assertNotNull(result);
    assertEquals(group, result.dispatchedGroup());
    assertEquals(0, result.payloadItems().size());
  }
}
