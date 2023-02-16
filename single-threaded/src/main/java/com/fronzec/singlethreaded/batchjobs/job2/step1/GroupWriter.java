package com.fronzec.singlethreaded.batchjobs.job2.step1;

import com.fronzec.singlethreaded.group.GroupedEntity;
import com.fronzec.singlethreaded.group.GroupedRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import org.springframework.batch.item.ItemWriter;

import java.util.List;

@StepScope
@Component
@Qualifier("job2Step1Writer")
public class GroupWriter implements ItemWriter<GroupedEntity> {
    
    private GroupedRepository repository;
    private static final Logger LOGGER = LoggerFactory.getLogger(GroupWriter.class);

    public GroupWriter(GroupedRepository repository) {
        this.repository = repository;
    }

    @Override
    public void write(List<? extends GroupedEntity> list) {
        LOGGER.info("writing {}", list.size());
        List<? extends GroupedEntity> groupedEntities = repository.persistAll(list);
        LOGGER.info("writed", groupedEntities.size());

    }
}
