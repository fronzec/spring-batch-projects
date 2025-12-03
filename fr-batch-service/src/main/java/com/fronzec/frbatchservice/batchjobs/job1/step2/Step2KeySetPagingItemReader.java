/* 2025 */
package com.fronzec.frbatchservice.batchjobs.job1.step2;

import com.fronzec.frbatchservice.person.PersonRepository;
import com.fronzec.frbatchservice.person.PersonsEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.data.AbstractPaginatedDataItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class Step2KeySetPagingItemReader extends AbstractPaginatedDataItemReader<PersonsEntity> {

    long lastPersonId = 0;

    @Value("${fr-batch-service.jobs.job1.step2.reader.chunk-size:1000}")
    private int chunkRead;

    private final PersonRepository personRepository;

    private List<PersonsEntity> persons = new ArrayList<>();

    /**
     * Create a Step2KeySetPagingItemReader backed by the given PersonRepository.
     *
     * The constructor sets the reader's name for Spring Batch and disables state saving,
     * since processed state is tracked by a column on the entity rather than reader position.
     *
     * @param personRepository repository used to page through PersonsEntity records
     */
    public Step2KeySetPagingItemReader(PersonRepository personRepository) {
        this.personRepository = personRepository;
        // note: required set the name for spring batch
        setName(Step2KeySetPagingItemReader.class.getName());
        setSaveState(
                false); // Because we are controlling if an item is processed or not using a column
        // value,
        // is preferable don't store any
        // state such as the current row number, since is irrelevant upon restart. This can bea
        // applicable for readers and writers
    }

    /**
     * Read the next page of PersonsEntity using key-set paging and return an iterator over the results.
     *
     * <p>Updates the reader's internal lastPersonId from the prior page and fetches the next batch of
     * unprocessed PersonsEntity records ordered by ascending ID.</p>
     *
     * @return an Iterator over the PersonsEntity records retrieved for the current page
     */
    @Override
    protected Iterator<PersonsEntity> doPageRead() {
        PageRequest pageRequest = PageRequest.of(0, chunkRead);
        lastPersonId = getLastChunkId(persons);
        persons =
                personRepository.findByIdGreaterThanAndProcessedIsFalseOrderByIdAsc(
                        lastPersonId, pageRequest);
        return persons.iterator();
    }

    /**
     * Get the ID of the last person in the provided page.
     *
     * @param persons the current page of persons; may be null or empty
     * @return the ID of the last person in the list, or 0 if the list is null or empty
     */
    private long getLastChunkId(List<PersonsEntity> persons) {
        if (persons == null || persons.isEmpty()) {
            return 0L;
        }
        return persons.get(persons.size() - 1).getId();
    }
}