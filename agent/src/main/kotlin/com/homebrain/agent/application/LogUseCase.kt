package com.homebrain.agent.application

import com.homebrain.agent.infrastructure.engine.EngineClient
import org.springframework.stereotype.Service

/**
 * Use case for log retrieval.
 * 
 * Provides access to automation execution logs from the engine.
 */
@Service
class LogUseCase(
    private val engineClient: EngineClient
) {

    /**
     * Gets recent log entries.
     */
    fun getLogs(): List<Map<String, Any>> {
        return engineClient.getLogs()
    }
}
