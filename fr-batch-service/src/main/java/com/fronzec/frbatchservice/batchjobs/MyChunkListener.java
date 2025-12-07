/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

@Component
public class MyChunkListener implements ChunkListener {

    private static final Logger logger = LoggerFactory.getLogger(MyChunkListener.class);

    @Override
    public void beforeChunk(ChunkContext context) {
        logger.info("Before chunk listener -> {}", context);
    }

    @Override
    public void afterChunk(ChunkContext context) {
        logger.info("After chunk listener -> {}", context);
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        logger.info("Error chunk listener -> {}", context);
    }
}
