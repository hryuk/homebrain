package com.homebrain.agent.application

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduler for synchronizing code embeddings with the filesystem.
 * 
 * - Runs on application startup to index all existing automations and libraries
 * - Periodically syncs to catch any filesystem changes not triggered through the API
 */
@Component
@EnableScheduling
class EmbeddingSyncScheduler(
    private val embeddingService: CodeEmbeddingService
) {
    private val logger = KotlinLogging.logger {}
    
    /**
     * Sync embeddings on application startup.
     * Uses @EventListener to ensure all beans are initialized.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() {
        logger.info { "Starting initial embedding sync..." }
        
        try {
            if (embeddingService.isReady()) {
                embeddingService.syncWithFilesystem()
            } else {
                logger.warn { "Embedding service not ready on startup. Embeddings will be disabled." }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync embeddings on startup" }
        }
    }
    
    /**
     * Periodic sync every 5 minutes (300,000 ms).
     * Catches any filesystem changes not made through the API.
     */
    @Scheduled(fixedRateString = "\${app.embeddings.sync-interval:300000}")
    fun periodicSync() {
        try {
            if (embeddingService.isReady()) {
                logger.debug { "Running periodic embedding sync..." }
                embeddingService.syncWithFilesystem()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync embeddings during periodic sync" }
        }
    }
}
