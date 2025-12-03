/* 2025 */
package com.fronzec.frbatchservice.batchjobs.job1.step3;

import com.fronzec.frbatchservice.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.frbatchservice.personv2.PersonsV2Entity;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class Step3Processor
        implements ItemProcessor<PersonsV2Entity, ProcessIndicatorItemWrapper<PayloadItemInfo>> {

    /**
     * Transforms a PersonsV2Entity into a ProcessIndicatorItemWrapper carrying a PayloadItemInfo.
     *
     * @param item the source person entity whose uuidV4, firstName, lastName, email, and profession are used to build the payload
     * @return a ProcessIndicatorItemWrapper whose process id is the entity's id and whose payload is a PayloadItemInfo built from the entity's fields
     */
    @Override
    public ProcessIndicatorItemWrapper<PayloadItemInfo> process(PersonsV2Entity item) {
        PayloadItemInfo payloadItemInfo =
                new PayloadItemInfo(
                        item.getUuidV4(),
                        item.getFirstName(),
                        item.getLastName(),
                        item.getEmail(),
                        item.getProfession());
        return new ProcessIndicatorItemWrapper<>(item.getId(), payloadItemInfo);
    }
}