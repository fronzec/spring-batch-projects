/* 2025 */
package com.fronzec.frbatchservice.batchjobs.job1.step2;

import com.fronzec.frbatchservice.batchjobs.persons.ProcessIndicatorItemWrapper;
import com.fronzec.frbatchservice.person.PersonsEntity;
import com.fronzec.frbatchservice.personv2.PersonsV2Entity;
import com.fronzec.frbatchservice.restclients.ApiClient;
import com.fronzec.frbatchservice.restclients.DataCalculatedResponse;
import java.time.LocalDate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class Step2PersonProcessor
        implements ItemProcessor<PersonsEntity, ProcessIndicatorItemWrapper<PersonsV2Entity>> {

    private final ApiClient apiClient;
    private final LocalDate processingDate;

    /**
     * Creates a processor configured with an API client and a processing date.
     *
     * @param processingDateStr the job parameter "DATE" as an ISO-8601 date string (yyyy-MM-dd); parsed into the processor's LocalDate
     */
    public Step2PersonProcessor(
            ApiClient apiClient, @Value("#{jobParameters['DATE']}") String processingDateStr) {
        this.apiClient = apiClient;
        this.processingDate = LocalDate.parse(processingDateStr);
    }

    /**
     * Converts a PersonsEntity into a PersonsV2Entity using the job processing date and an external calculated value, and returns it wrapped with the original entity id.
     *
     * @param item the source PersonsEntity to process
     * @return a ProcessIndicatorItemWrapper containing the source id and the mapped PersonsV2Entity
     */
    @Override
    public ProcessIndicatorItemWrapper<PersonsV2Entity> process(PersonsEntity item) {
        DataCalculatedResponse randomValue = apiClient.getRandomValue(item.getId());
        PersonsV2Entity personsV2Entity =
                PersonToPersonv2Mapper.fromTo(processingDate, item, randomValue.getValue());
        return new ProcessIndicatorItemWrapper<>(item.getId(), personsV2Entity);
    }
}
